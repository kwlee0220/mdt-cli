package mdt.cli.start;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.workflow.Workflow;
import mdt.workflow.WorkflowManager;

/**
 * 워크플로우 모델로부터 새 워크플로우 인스턴스를 생성하고 실행을 시작하는 CLI 명령({@code workflow}).
 * <p>
 * 시작된 인스턴스의 요약 정보(이름, 모델 ID, 상태)는 사람이 읽기 쉬운 형태로 표준 출력에 기록된다.
 * 지정한 모델 ID가 존재하지 않으면 사용자에게 명확한 메시지를 표시하고 종료한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "workflow",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Start an MDT Workflow."
)
public class StartWorkflowCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StartWorkflowCommand.class);

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 사용자 친화적 오류 메시지를 생성하기 위해
	 * {@link CommandLine}을 얻을 때 사용한다.
	 */
	@Spec private CommandSpec m_spec;

	/** 시작할 워크플로우 모델의 식별자. 필수 인자. */
	@Parameters(index="0", arity="1", paramLabel="model-id", description="Workflow model id to start")
	private String m_wfModelId;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception picocli 구성 또는 명령 실행 중 발생한 예외.
	 */
	public static void main(String... args) throws Exception {
		main(new StartWorkflowCommand(), args);
	}

	/**
	 * 이 명령의 로거를 {@code StartWorkflowCommand} 이름으로 초기화하는 생성자.
	 */
	public StartWorkflowCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령 본체. {@code mdt}로부터 {@link WorkflowManager}를 얻어 모델 ID로 워크플로우를
	 * 시작한 뒤, 결과 {@link Workflow}의 요약 정보를 표준 출력으로 기록한다.
	 *
	 * @param mdt 부모 클래스가 접속을 완료한 {@link MDTManager}.
	 * @throws CommandLine.ParameterException 지정된 모델 ID에 해당하는 워크플로우 모델이 존재하지 않는 경우.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();

		Workflow wf;
		try {
			wf = wfMgr.startWorkflow(m_wfModelId);
		}
		catch ( ResourceNotFoundException e ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Workflow model not found: " + m_wfModelId);
		}

		printWorkflowSummary(wf);
	}

	/**
	 * {@link Workflow}의 핵심 정보(이름, 모델 ID, 상태)를 사람이 읽기 좋은 다중 라인 형식으로 출력한다.
	 *
	 * @param wf 출력할 워크플로우.
	 */
	private void printWorkflowSummary(Workflow wf) {
		System.out.printf("Started workflow:%n");
		System.out.printf("  name:   %s%n", wf.getName());
		System.out.printf("  model:  %s%n", wf.getModelId());
		System.out.printf("  status: %s%n", wf.getStatus());
	}

	/**
	 * 검증 메시지 발생용 {@link CommandLine}을 반환한다. picocli가 정상적으로 파싱한 경우 주입된
	 * {@link #m_spec}에서 가져오고, 그렇지 않으면 새로 생성한 인스턴스를 사용한다.
	 *
	 * @return 이 명령에 연결된 {@link CommandLine}.
	 */
	private CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
}
