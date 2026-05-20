package mdt.cli.add;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.ResourceAlreadyExistsException;
import mdt.model.ResourceNotFoundException;
import mdt.workflow.WorkflowManager;
import mdt.workflow.WorkflowModel;

/**
 * 워크플로우 모델 정의를 담은 JSON 파일을 읽어 MDT 시스템에 등록하는 CLI 명령({@code wfmodel}).
 * <p>
 * 동일한 ID의 모델이 이미 등록되어 있는 경우 기본적으로 사용자에게 명확한 메시지와 함께 종료한다.
 * {@code --force}/{@code -f}를 지정하면 기존 모델을 먼저 삭제한 뒤 새 모델을 등록한다(덮어쓰기).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "wfmodel",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Add an MDT Workflow model."
)
public class AddWorkflowModelCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(AddWorkflowModelCommand.class);

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 사용자 친화적 오류 메시지를 생성하기 위해
	 * {@link CommandLine}을 얻을 때 사용한다.
	 */
	@Spec private CommandSpec m_spec;

	/** 등록할 워크플로우 모델이 정의된 JSON 파일 경로. 필수 인자. */
	@Parameters(index="0", arity="1", paramLabel="path",
				description="Path to the workflow model JSON file to register")
	private File m_file;

	/**
	 * 동일 ID의 모델이 이미 등록되어 있을 때 기존 모델을 덮어쓸지 여부.
	 * {@code false}이면 명확한 메시지와 함께 종료된다.
	 */
	@Option(names={"--force", "-f"}, description="Overwrite the existing workflow model if it exists")
	private boolean m_force = false;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception picocli 구성 또는 명령 실행 중 발생한 예외.
	 */
	public static void main(String... args) throws Exception {
		main(new AddWorkflowModelCommand(), args);
	}

	/**
	 * 이 명령의 로거를 {@code AddWorkflowModelCommand} 이름으로 초기화하는 생성자.
	 */
	public AddWorkflowModelCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령 본체. 입력 JSON 파일을 검증·파싱하여 {@link WorkflowModel}을 만든 뒤
	 * {@link WorkflowManager#addWorkflowModel(WorkflowModel)}로 등록한다. 충돌 발생 시
	 * {@code --force} 정책에 따라 기존 모델을 삭제하고 한 번 재시도한다.
	 *
	 * @param mdt 부모 클래스가 접속을 완료한 {@link MDTManager}.
	 * @throws CommandLine.ParameterException 파일 접근 불가, JSON 파싱 실패, 충돌 모델 존재 시
	 *                                        {@code --force} 미지정, 또는 덮어쓰기 중 기존 모델 삭제 실패,
	 *                                        또는 덮어쓰기 직후 동시 충돌(race condition).
	 * @throws Exception 그 외 등록 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		validateInputFile();

		getLogger().debug("parsing workflow model file: {}", m_file.getAbsolutePath());
		WorkflowModel wfDesc;
		try {
			wfDesc = WorkflowModel.parseJsonFile(m_file);
		}
		catch ( IOException e ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Failed to parse workflow model JSON: " + m_file.getAbsolutePath()
					+ ", cause=" + e.getMessage());
		}

		WorkflowManager wfMgr = mdt.getWorkflowManager();
		getLogger().debug("adding workflow model: id={}", wfDesc.getId());
		try {
			wfMgr.addWorkflowModel(wfDesc);
		}
		catch ( ResourceAlreadyExistsException e ) {
			if ( !m_force ) {
				throw new CommandLine.ParameterException(getCommandLine(),
						"Workflow model already exists: " + wfDesc.getId() + ". Use --force to overwrite.");
			}
			getLogger().debug("overwrite requested; removing existing workflow model: id={}", wfDesc.getId());
			try {
				wfMgr.removeWorkflowModel(wfDesc.getId());
			}
			catch ( ResourceNotFoundException rmErr ) {
				throw new CommandLine.ParameterException(getCommandLine(),
						"Failed to remove existing workflow model for overwrite: "
						+ wfDesc.getId() + ", cause=" + rmErr.getMessage());
			}
			getLogger().debug("retrying addWorkflowModel after remove: id={}", wfDesc.getId());
			try {
				wfMgr.addWorkflowModel(wfDesc);
			}
			catch ( ResourceAlreadyExistsException retry ) {
				throw new CommandLine.ParameterException(getCommandLine(),
						"Workflow model was re-created concurrently during overwrite: " + wfDesc.getId());
			}
		}
	}

	/**
	 * 입력 파일이 등록 가능한 상태인지 검사한다. 다음 세 가지 조건을 차례로 확인하여
	 * 위반 시 사용자 친화적 메시지로 {@link CommandLine.ParameterException}을 발생시킨다.
	 *
	 * @throws CommandLine.ParameterException 다음 중 하나에 해당하는 경우:
	 *         <ul>
	 *           <li>파일이 존재하지 않는 경우</li>
	 *           <li>일반 파일이 아닌 경우(디렉터리 또는 특수 파일 등)</li>
	 *           <li>읽기 권한이 없는 경우</li>
	 *         </ul>
	 */
	private void validateInputFile() {
		if ( !m_file.exists() ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"File not found: " + m_file.getAbsolutePath());
		}
		if ( !m_file.isFile() ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Not a regular file: " + m_file.getAbsolutePath());
		}
		if ( !m_file.canRead() ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Cannot read file: " + m_file.getAbsolutePath());
		}
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
