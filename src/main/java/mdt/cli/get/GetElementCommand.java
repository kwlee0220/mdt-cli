package mdt.cli.get;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;

/**
 * MDT 표현식이 가리키는 SubmodelElement의 정보를 조회하는 CLI 명령.
 * <p>
 * 대상은 위치 인자 하나로 받은 MDT 표현식이다. {@link ElementReferences#parseExpr(String)}이 받는 모든 참조식을
 * 쓸 수 있다.
 * <ul>
 *   <li>{@code <instance>:<submodel>:<idShortPath>} — 예: {@code test:Data:DataInfo.Temperature}</li>
 *   <li>{@code param:<instance>:<parameter>} — 예: {@code param:test:Data}, {@code param:test:*}</li>
 *   <li>{@code oparg:<instance>:<submodel>:(in|out):<argument>}</li>
 *   <li>{@code opvar:...}, {@code timeseries:<instance>:<submodel>#last=5}, {@code submodel:id=<id>:<path>}</li>
 * </ul>
 * 참조를 활성화하여 상위 클래스의 출력 로직({@code tree}/{@code json}/{@code value})으로 위임한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "element",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get SubmodelElement information."
)
public class GetElementCommand extends AbstractGetElementCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetElementCommand.class);

	/** 조회 대상 MDT 표현식. */
	@Parameters(index = "0", paramLabel = "element-expr",
				description = "MDT element expression "
							+ "(e.g. inst:Data:DataInfo.Temperature, param:inst:Data, 'param:inst:*', "
							+ "oparg:inst:AI:in:Image, 'timeseries:inst:Log#last=5')")
	private String m_elementExpr;

	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private ElementOutputType m_output = ElementOutputType.tree;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception 명령 실행 중 발생한 예외.
	 */
	public static final void main(String... args) throws Exception {
		main(new GetElementCommand(), args);
	}

	/**
	 * 기본 로거를 설정하는 생성자.
	 */
	public GetElementCommand() {
		setLogger(s_logger);
	}

	/**
	 * MDT 표현식을 {@link ElementReference}로 파싱하여 상위 클래스의 출력 루틴으로 위임한다.
	 *
	 * @param mdt 접속이 완료된 {@link MDTManager} 인스턴스.
	 * @throws Exception 참조 파싱, 활성화, 또는 출력 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		// 활성화는 AbstractGetElementCommand.run()이 한다.
		run(manager, ElementReferences.parseExpr(m_elementExpr));
	}

	@Override
	protected void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception {
		printElement(smeRef, pw, m_output);
	}
}
