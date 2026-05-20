package mdt.cli.add;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.ResourceAlreadyExistsException;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerException;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 새 MDT 인스턴스를 추가 등록하는 CLI 명령({@code instance}).
 * <p>
 * 인스턴스 ID와 구현체 경로(디렉터리 또는 zip 파일)를 인자로 받아 {@link MDTInstanceManager}에
 * 등록한다. 필요 시 포트 번호를 함께 지정할 수 있다. {@code -v}를 주면 등록 성공 메시지를 출력한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "instance",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Add an MDT instance."
)
public class AddMDTInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(AddMDTInstanceCommand.class);

	/** 등록할 MDTInstance의 식별자. 필수 인자. */
	@Parameters(index="0", arity="1", paramLabel="id",
				description="Identifier of the MDTInstance to register")
	private String m_id;

	/** 등록할 구현체의 경로. 디렉터리 또는 zip 파일을 지정한다. 필수 인자. */
	@Parameters(index="1", arity="1", paramLabel="path",
				description="Path to the MDTInstance implementation directory or zip file")
	private File m_instanceFile;

	/**
	 * MDTInstance가 사용할 포트 번호. {@code -1}이면 서버가 자동으로 포트를 할당하거나 기본값을 사용한다.
	 */
	@Option(names={"--port", "-p"}, paramLabel="number",
			description="Port number for the MDTInstance (-1 for auto-assign, default: ${DEFAULT-VALUE})",
			defaultValue = "-1")
	private int m_port = -1;

	/** 등록 성공 메시지를 출력할지 여부. */
	@Option(names={"-v"}, description="Verbose output on success")
	private boolean m_verbose = false;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception picocli 구성 또는 명령 실행 중 발생한 예외.
	 */
	public static void main(String... args) throws Exception {
		main(new AddMDTInstanceCommand(), args);
	}

	/**
	 * 이 명령의 로거를 {@code AddMDTInstanceCommand} 이름으로 초기화하는 생성자.
	 */
	public AddMDTInstanceCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령 본체. {@link MDTInstanceManager#addInstance(String, int, File)}로 새 인스턴스를 등록한다.
	 * 등록 실패 시 사용자 친화적 메시지를 갖는 {@link CommandLine.ParameterException}으로 변환한다.
	 * {@code -v}가 지정된 경우 등록 완료 메시지를 표준 출력으로 기록한다.
	 *
	 * @param manager 부모 클래스가 접속을 완료한 {@link MDTManager}.
	 * @throws CommandLine.ParameterException 인스턴스 파일 미지정·접근 불가 등 사용자 입력 문제로 등록이 실패한 경우.
	 * @throws Exception 그 외 등록 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager manager) throws Exception {
		MDTInstanceManager instanceManager = manager.getInstanceManager();
		try {
			instanceManager.addInstance(m_id, m_port, m_instanceFile);
			if ( m_verbose ) {
				System.out.printf("Added MDTInstance: id=%s%n", m_id);
			}
		}
		catch ( ResourceAlreadyExistsException e ) {
			System.err.printf("MDTInstance is already exist: id=%s.%n", m_id);
		}
		catch ( MDTInstanceManagerException e ) {
			Throwable cause = e.getCause();
			System.err.printf("Failed to add MDTInstance: id=%s, cause=%s.%n",
								m_id, (cause != null) ? cause.getMessage() : e.getMessage());
		}

	}
}
