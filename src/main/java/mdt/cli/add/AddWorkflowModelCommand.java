package mdt.cli.add;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.ResourceAlreadyExistsException;
import mdt.workflow.WorkflowManager;
import mdt.workflow.WorkflowModel;

/**
 * 워크플로우 모델 정의를 담은 JSON 파일을 읽어 MDT 시스템에 등록하는 CLI 명령({@code wfmodel}).
 * <p>
 * 동일한 ID의 모델이 이미 등록되어 있는 경우 기본적으로 사용자에게 명확한 메시지와 함께 종료한다.
 * {@code --force}/{@code -f}를 지정하면 같은 id의 기존 모델을 서버에서 교체한다(덮어쓰기). 교체가 실패하면 서버가
 * 기존 모델을 유지하거나 되돌린다.
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
	 * {@link WorkflowManager#addWorkflowModel(WorkflowModel)}로 등록한다. {@code --force}이면
	 * {@link WorkflowManager#addOrReplaceWorkflowModel(WorkflowModel)}로 기존 모델을 한 번에 교체한다.
	 *
	 * @param mdt 부모 클래스가 접속을 완료한 {@link MDTManager}.
	 * @throws CommandLine.ParameterException 파일 접근 불가, JSON 파싱 실패, 또는 {@code --force} 없이
	 *                                        같은 id의 모델이 이미 있는 경우.
	 * @throws Exception 그 외 등록 중 발생한 예외.
	 */
	@Override
	protected void validate() {
		validateInputFile();
	}

	@Override
	public void run(MDTManager mdt) throws Exception {

		getLogger().debug("parsing workflow model file: {}", m_file.getAbsolutePath());
		WorkflowModel wfDesc;
		try {
			wfDesc = WorkflowModel.parseJsonFile(m_file);
		}
		catch ( IOException e ) {
			throw usageError("Failed to parse workflow model JSON: " + m_file.getAbsolutePath()
					+ ", cause=" + e.getMessage());
		}

		WorkflowManager wfMgr = mdt.getWorkflowManager();
		getLogger().debug("adding workflow model: id={}, force={}", wfDesc.getId(), m_force);
		if ( m_force ) {
			// 서버가 같은 모델을 한 트랜잭션으로 갱신하고, 후처리(DAG 생성)가 실패하면 이전 모델로 되돌린다.
			wfMgr.addOrReplaceWorkflowModel(wfDesc);
			return;
		}
		try {
			wfMgr.addWorkflowModel(wfDesc);
		}
		catch ( ResourceAlreadyExistsException e ) {
			throw usageError("Workflow model already exists: " + wfDesc.getId() + ". Use --force to overwrite.");
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
			throw usageError("File not found: " + m_file.getAbsolutePath());
		}
		if ( !m_file.isFile() ) {
			throw usageError("Not a regular file: " + m_file.getAbsolutePath());
		}
		if ( !m_file.canRead() ) {
			throw usageError("Cannot read file: " + m_file.getAbsolutePath());
		}
	}

}
