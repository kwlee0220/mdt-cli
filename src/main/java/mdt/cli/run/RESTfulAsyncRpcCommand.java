package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Maps;

import okhttp3.OkHttpClient;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import utils.Throwables;
import utils.http.OkHttpClientUtils;
import utils.rpc.restful.RESTfulAsyncRpcClient;
import utils.rpc.restful.RpcRequestMessage;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.expr.MDTParserException;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerAware;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;
import mdt.model.sm.value.FileValue;
import mdt.task.TaskArgument;
import mdt.task.TaskArgument.ReferenceArgument;


/**
 * 원격 서버가 제공하는 연산을 RESTful 비동기 RPC로 호출하는 커맨드.
 * <p>
 * {@code --baseUrl}과 {@code --endpoint}로 지정된 연산을 {@link RESTfulAsyncRpcClient}로 호출한다.
 * 최초 POST 요청으로 연산을 시작시킨 뒤 완료될 때까지 {@code --poll} 주기로 상태를 조회하며,
 * 완료되면 응답에 포함된 출력 값을 {@code --out.<name>}이 가리키는 MDT 요소에 기록한다.
 * <p>
 * 태스크 인자는 다음과 같이 해석된다:
 * <ul>
 * 	<li>{@code --in.<name>=<expr>}: MDT 요소 참조 표현식이면 그 값을 읽어 전송하고,
 * 		그렇지 않으면 JSON 리터럴로 해석하여 전송한다.</li>
 * 	<li>{@code --out.<name>=<ref-expr>}: MDT 요소 참조 표현식만 허용된다. 요청 시점의 값이
 * 		출력 값의 타입을 결정하는 prototype으로 사용된다.</li>
 * </ul>
 * {@link RESTfulAsyncRpcClient}가 지원하지 않기 때문에 {@code --inout.<name>}은 사용할 수 없다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "restful",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "RESTful-based async RPC execution command."
)
public class RESTfulAsyncRpcCommand extends DynamicArgumentsCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulAsyncRpcCommand.class);

	@Option(names={"--baseUrl"}, paramLabel="baseURL", required=true, description="base URL for the server")
	private String m_baseUrl;

	@Option(names={"--endpoint"}, paramLabel="endpoint", required=true, description="The operation endpoint")
	private String m_endpoint = null;

	@Mixin private OperationRunOptions m_runOptions;

	public RESTfulAsyncRpcCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		Instant started = Instant.now();

		// Command line 인자를로부터 전달된 input/output/inoutput 변수 값을 수집한다.
		TaskCommandArguments cmdArgs = loadTaskArgumentsFromCommandLine();

		// inoutput 변수는 RESTfulAsyncRpcClient에서 처리할 수 없으므로, input/output 변수만을 대상으로 한다.
		if ( !cmdArgs.getInoutputs().isEmpty() ) {
			throw new IllegalArgumentException("inoutput operation variables are not supported: "
												+ cmdArgs.getInoutputs().keySet());
		}

		JsonMapper mapper = MDTModelSerDe.getJsonMapper();

		Map<String,JsonNode> inputs = KeyValueFStream.from(cmdArgs.getInputs())
														.mapValue((k,argStr) -> {
															try {
																return loadInputArgument(manager, argStr);
															}
															catch ( IOException e ) {
																return Throwables.sneakyThrow(e);
															}
														})
														.toMap();

		// 출력 인자의 요청 시점 값은 요청 메시지에 포함되는 동시에, 응답 값에 '@type'이 없는 경우
		// 값의 타입을 결정하는 prototype으로도 사용되기 때문에 함께 보관한다.
		Map<String,OutputArgument> outArgs = Maps.newHashMap();
		Map<String,JsonNode> outputs = Maps.newHashMap();
		KeyValueFStream.from(cmdArgs.getOutputs())
						.forEachOrThrow((argId, argStr) -> {
							ReferenceArgument refArg = TaskArgument.reference(argStr);
							refArg = MDTInstanceManagerAware.activate(refArg, manager);

							ElementValue argv = refArg.readValue();
							outArgs.put(argId, new OutputArgument(refArg, argv));
							outputs.put(argId, ( argv instanceof FileValue )
													? refArg.getElementReference().toJsonNode()
													: argv.toJsonNode());
						});

		RpcRequestMessage reqMsg = new RpcRequestMessage(inputs, outputs);
		OkHttpClient httpClient = OkHttpClientUtils.newClient();
		Map<String,JsonNode> outValues;
		try {
			RESTfulAsyncRpcClient client = RESTfulAsyncRpcClient.builder()
															.setHttpClient(httpClient)
															.setBaseUrl(m_baseUrl)
															.setOperationEndpoint(m_endpoint)
															.setPollInterval(m_runOptions.getPollInterval())
															.setTimeout(m_runOptions.getTimeout())
															.setRequestMessage(reqMsg)
															.setJsonMapper(mapper)
															.build();
			outValues = client.run();
		}
		finally {
			shutdownQuietly(httpClient);
		}

		// 아래 match()는 양쪽에 모두 존재하는 이름만 처리하기 때문에, 요청한 출력 변수 중 응답에 포함되지
		// 않은 것과 요청하지 않았는데 응답에 포함된 것은 조용히 무시된다. 이를 사용자에게 알린다.
		warnUnmatchedOutputs("missing in the rpc response", outArgs.keySet(), outValues.keySet());
		warnUnmatchedOutputs("not requested but returned", outValues.keySet(), outArgs.keySet());

		// 출력 변수의 값을 수집된 출력 값으로 갱신한다.
		KeyValueFStream.from(outArgs)
						.match(outValues)
						.forEach((argId, match) -> {
							OutputArgument outArg = match._1;
							try {
								ElementValue updated = update(outArg, match._2);

								if ( m_runOptions.getShowResult() ) {
									System.out.printf("%s: %s%n", argId, updated.toDisplayString());
								}
							}
							catch ( IOException e ) {
								getLogger().warn("Failed to update output argument: id={}, cause={}", argId, e);
							}
						});

		Duration elapsed = Duration.between(started, Instant.now());
		getLogger().info("Rpc[{}{}], elapsedTime={}", m_baseUrl, m_endpoint, elapsed);
	}

	/**
	 * 명령행에서 지정된 출력 인자 명세와 요청 시점에 읽은 그 값.
	 *
	 * @param argSpec	출력 인자가 가리키는 MDT 요소 참조.
	 * @param proto		요청 시점에 읽은 값. 응답 값에 {@code @type}이 없는 경우 값의 타입 결정에 사용된다.
	 */
	private record OutputArgument(ReferenceArgument argSpec, ElementValue proto) { }

	private static void shutdownQuietly(OkHttpClient httpClient) {
		// OkHttpClient는 non-daemon thread를 사용하는 dispatcher와 connection pool을 갖기 때문에
		// 명시적으로 정리하지 않으면 커맨드 수행이 끝나도 JVM이 종료되지 않을 수 있다.
		httpClient.dispatcher().executorService().shutdown();
		httpClient.connectionPool().evictAll();
	}

	private void warnUnmatchedOutputs(String reason, Set<String> argIds, Set<String> counterparts) {
		List<String> unmatcheds = FStream.from(argIds)
										.filterNot(counterparts::contains)
										.sort()
										.toList();
		if ( !unmatcheds.isEmpty() ) {
			getLogger().warn("output operation variable(s) {}: {}", reason, unmatcheds);
		}
	}

	private JsonNode loadInputArgument(MDTInstanceManager manager, String argStr) throws IOException {
		MDTElementReference ref;
		try {
			ref = ElementReferences.parseExpr(argStr);
		}
		catch ( MDTParserException expected ) {
			// 참조 표현식이 아닌 경우는 JSON 리터럴로 해석한다.
			try {
				return MDTModelSerDe.getJsonMapper().readTree(argStr);
			}
			catch ( JsonProcessingException e ) {
				// 유효한 JSON이 아닌 경우는 따옴표가 생략된 문자열 리터럴로 간주한다.
				// (즉, '--in.name=hello'를 '--in.name="hello"'와 동일하게 취급한다.)
				return TextNode.valueOf(argStr);
			}
		}

		ref.activate(manager);
		ElementValue smev = ref.readValue();

		// FileValue인 경우는 참조 자체를, 그렇지 않은 경우는 읽어온 값을 JSON 객체로 변환한다.
		return ( smev instanceof FileValue ) ? ref.toJsonNode() : smev.toJsonNode();
	}

	private ElementValue update(OutputArgument outArg, JsonNode outNode) throws IOException {
		// '@type' 속성이 없는 경우는 요청 시점에 읽어둔 값의 타입을 그대로 사용한다.
		ElementValue outValue = ElementValues.parseGeneralJsonNode(outNode, outArg::proto);
		outArg.argSpec().updateValue(outValue);

		return outValue;
	}

	public static void main(String... args) throws Exception {
		main(new RESTfulAsyncRpcCommand(), args);
		System.exit(0);
	}
}
