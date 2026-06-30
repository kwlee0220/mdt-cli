package mdt.cli.get;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;

/**
 * 특정 MDTInstance에 속한 Submodel 또는 SubmodelElement의 정보를 조회하는 CLI 명령.
 * <p>
 * 조회 대상은 다음 두 가지 방식 중 하나로 지정한다(택일, 필수).
 * <ul>
 *   <li>세 개의 위치 인자({@code instance}, {@code submodel}, {@code path}): 모두 필수이며
 *       {@code "instance:submodel:path"} 형식의 표현식을 구성한다.
 *       ({@code submodel}/{@code path}에 {@code "*"}를 명시하면 해당 범위 전체를 의미한다.)</li>
 *   <li>{@code --ref <ref_expr>}: {@link ElementReference} 표현식을 직접 지정한다
 *       (예: {@code param:inst:*}, {@code timeseries:inst:Data:Tail#last=5|current}).</li>
 * </ul>
 * 구성/지정된 표현식을 {@link ElementReferences#parseExpr(String)}으로 {@link ElementReference}로 변환한 뒤
 * 활성화하여 상위 클래스의 출력 로직({@code tree}/{@code json}/{@code value})으로 위임한다.
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

	/** 조회 대상 지정: (instance submodel path) 위치 인자 묶음 또는 {@code --ref} 중 하나(택일, 필수). */
	@ArgGroup(exclusive = true, multiplicity = "1")
	private TargetSpec m_target;

	/** 위치 인자 묶음과 {@code --ref} 옵션을 배타적으로 묶는 그룹. */
	static class TargetSpec {
		/** 위치 인자 3개 (함께 사용). */
		@ArgGroup(exclusive = false)
		private PositionalSpec positional;

		/** ElementReference 표현식 직접 지정. */
		@Option(names = {"--ref"}, paramLabel = "ref_expr",
				description = "ElementReference expression "
							+ "(e.g. param:inst:param1, timeseries:inst:Log:Tail#last=5)")
		private String refExpr;
	}

	/** {@code instance submodel path} 위치 인자 묶음. */
	static class PositionalSpec {
		/** 조회 대상 MDTInstance의 식별자. */
		@Parameters(index = "0", paramLabel = "instance", description = "MDTInstance id to show.")
		private String instanceId;

		/** 조회 대상 Submodel의 idShort. 필수. {@code "*"}를 명시하면 모든 Submodel을 의미한다. */
		@Parameters(index = "1", paramLabel = "submodel", description = "Target submodel idShort")
		private String smIdShort;

		/** 조회 대상 SubmodelElement의 idShortPath. 필수. {@code "*"}를 명시하면 Submodel 전체를 의미한다. */
		@Parameters(index = "2", paramLabel = "path", description = "Target SubmodelElement idShortPath")
		private String path;
	}

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
	 * 위치 인자 또는 {@code --ref}로부터 {@link ElementReference}를 구성/활성화한 뒤 상위 클래스의
	 * 출력 루틴으로 위임한다.
	 *
	 * @param mdt 접속이 완료된 {@link MDTManager} 인스턴스.
	 * @throws Exception 참조 파싱, 활성화, 또는 출력 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		String elmRefExpr;
		if ( m_target.refExpr != null ) {
			elmRefExpr = m_target.refExpr;
		}
		else {
			PositionalSpec p = m_target.positional;
			elmRefExpr = String.format("%s:%s:%s", p.instanceId, p.smIdShort, p.path);
		}

		ElementReference smeRef = ElementReferences.parseExpr(elmRefExpr);
		((MDTElementReference)smeRef).activate(manager);

		run(manager, smeRef);
	}
}
