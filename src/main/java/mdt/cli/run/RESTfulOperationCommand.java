package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import utils.KeyValue;
import utils.UnitUtils;
import utils.http.OkHttpClientUtils;
import utils.rpc.restful.RESTfulAsyncRpcClient;
import utils.rpc.restful.RpcRequestMessage;
import utils.stream.KeyValueFStream;

import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerAware;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;
import mdt.model.sm.value.FileValue;
import mdt.task.builtin.MultiVariablesCommand;
import mdt.workflow.model.ArgumentSpec;
import mdt.workflow.model.ArgumentSpec.LiteralArgumentSpec;
import mdt.workflow.model.ArgumentSpec.ReferenceArgumentSpec;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "restful",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "RESTful-based operation execution command."
)
public class RESTfulOperationCommand extends MultiVariablesCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulOperationCommand.class);
	private static final String DEFAULT_POLL_INTERVAL = "1s";

	@Option(names={"--baseUrl"}, paramLabel="baseURL",
			description="base URL for the server")
	private String m_baseUrl;

	@Option(names={"--endpoint"}, paramLabel="endpoint",
			description="The operation endpoint")
	private String m_endpoint = null;
	
	@Option(names={"--timeout"}, paramLabel="duration", description="Invocation timeout (e.g. \"30s\", \"1m\")")
	public void setTimeout(String timeout) {
		m_timeout = UnitUtils.parseDuration(timeout);
	}
	protected Duration m_timeout = null;

	@Option(names={"--poll"}, paramLabel="interval", defaultValue=DEFAULT_POLL_INTERVAL,
			description="Status polling interval (e.g. default=" + DEFAULT_POLL_INTERVAL + ")")
	public void setPollInterval(String interval) {
		m_pollInterval = UnitUtils.parseDuration(interval);
	}
	private Duration m_pollInterval;

	@Option(names={"--showResult"}, description="show output/inoutput operation variables")
	private boolean m_showResult = false;
	
	public RESTfulOperationCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		// Command line 인자를로부터 전달된 input/output/inoutput 변수 값을 수집한다.
		TaskArgumentsDescriptor tvsDesc = loadTaskArgumentsFromCommandLine();
		
		JsonMapper mapper = MDTModelSerDe.getJsonMapper();
		
		// Command line의 input 변수 값을 input OperationVariable에 설정한다.
		var inputs = KeyValueFStream.from(tvsDesc.getInputs())
								    .mapOrThrow(kv -> KeyValue.of(kv.key(), toJsonNode(manager, kv.value())))
								    .toKeyValueStream(kv -> kv)
								    .toMap();

		var outputs = KeyValueFStream.from(tvsDesc.getOutputs())
								    .mapOrThrow(kv -> KeyValue.of(kv.key(), toJsonNode(manager, kv.value())))
								    .toKeyValueStream(kv -> kv)
								    .toMap();
		RpcRequestMessage reqMsg = new RpcRequestMessage(inputs, outputs);
		
		RESTfulAsyncRpcClient client = RESTfulAsyncRpcClient.builder()
														.setHttpClient(OkHttpClientUtils.newClient())
														.setBaseUrl(m_baseUrl)
														.setOperationEndpoint(m_endpoint)
														.setPollInterval(m_pollInterval)
														.setTimeout(m_timeout)
														.setRequestMessage(reqMsg)
														.setJsonMapper(mapper)
														.build();
		Map<String,JsonNode> outValues = client.run();

		// 출력 변수의 값을 수집된 출력 값으로 갱신한다.
		KeyValueFStream.from(tvsDesc.getOutputs())
						.match(outValues)
						.forEach((argId, match) -> {
							ReferenceArgumentSpec outArgSpec = match._1;
							outArgSpec.activate(manager);
							
							try {
								ElementValue updated = update(outArgSpec, match._2);
								
								if ( m_showResult ) {
									System.out.printf("%s: %s%n", argId, updated.toValueObject());
								}
							}
							catch ( IOException e ) {
								getLogger().warn("Failed to update output argument: id={}, cause={}", argId, e);
							}
						});
	}
	
	private JsonNode toJsonNode(MDTInstanceManager manager, ArgumentSpec argSpec) throws IOException {
		if ( argSpec instanceof ReferenceArgumentSpec refArg ) {
			refArg = MDTInstanceManagerAware.activate(refArg, manager);
			ElementValue argv = refArg.readValue();
			if ( argv instanceof FileValue ) {
				return refArg.getElementReference().toJsonNode();
			}
			else {
				return argv.toJsonNode();
			}
		}
		else if ( argSpec instanceof LiteralArgumentSpec litArg ) {
			return MDTModelSerDe.getJsonMapper().valueToTree(litArg.readValue().toValueObject());
		}
		else {
			throw new IllegalArgumentException("Unexpected argument spec: " + argSpec.getClass().getName());
		}
	}
	
	private ElementValue update(ReferenceArgumentSpec outArgSpec, JsonNode outNode) throws IOException {
		ElementValue outValue = null;
		
		String typeStr = outNode.get("@type").asText();
		if ( typeStr == null || typeStr.isEmpty() ) {
			ElementValue proto = outArgSpec.readValue();
			outValue = ElementValues.parseValueJsonNode(outNode, proto);
		}
		else if ( typeStr.startsWith("mdt:value:") ) {
			outValue = ElementValues.parseJsonNode(outNode);
		}
		else {
			throw new IllegalArgumentException("Unexpected output value type: " + typeStr);
		}
		
		outArgSpec.updateValue(outValue);
		return outValue;
	}

	public static void main(String... args) throws Exception {
		main(new RESTfulOperationCommand(), args);
		System.exit(0);
	}
}
