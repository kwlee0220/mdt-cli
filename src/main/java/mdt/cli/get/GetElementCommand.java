package mdt.cli.get;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.SubmodelBasedElementReference;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 특정 MDTInstance에 속한 Submodel 또는 SubmodelElement의 정보를 조회하는 CLI 명령.
 * <p>
 * 세 개의 위치 인자({@code instance}, {@code submodel}, {@code path})를 받아
 * {@code "instance:submodel:path"} 형식의 표현식을 구성한 뒤,
 * {@link ElementReferences#parseExpr(String)}을 통해 {@link ElementReference}로 변환한다.
 * 이후 해당 참조를 활성화하여 상위 클래스의 출력 로직({@code tree}/{@code json}/{@code value})으로 위임한다.
 * <p>
 * {@code submodel}과 {@code path}는 생략 시 {@code "*"}로 동작하며, 이 경우 해당 범위 전체가 조회 대상이 된다.
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

	/** 조회 대상 MDTInstance의 식별자. */
	@Parameters(index="0", paramLabel="instance", description="MDTInstance id to show.")
	private String m_instanceId;

	/** 조회 대상 Submodel의 idShort. {@code "*"}이면 모든 Submodel을 의미한다. */
	@Parameters(index="1", paramLabel="submodel", defaultValue="*", description="Target submodel idShort")
	private String m_smIdShort;

	/** 조회 대상 SubmodelElement의 idShortPath. {@code "*"}이면 Submodel 전체를 의미한다. */
	@Parameters(index="2", paramLabel="path", defaultValue="*", description="Target SubmodelElement idShortPath")
	private String m_path;

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
	 * 위치 인자로부터 {@link ElementReference}를 구성/활성화한 뒤 상위 클래스의 출력 루틴으로 위임한다.
	 *
	 * @param mdt 접속이 완료된 {@link MDTManager} 인스턴스.
	 * @throws Exception 참조 파싱, 활성화, 또는 출력 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		String elmRef = String.format("%s:%s:%s", m_instanceId, m_smIdShort, m_path);
		ElementReference smeRef = ElementReferences.parseExpr(elmRef);

		((SubmodelBasedElementReference)smeRef).activate(manager);

		run(manager, smeRef);
	}
}
