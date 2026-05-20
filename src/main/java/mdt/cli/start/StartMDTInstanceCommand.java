package mdt.cli.start;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.client.instance.StartInstances;
import mdt.client.instance.StartMDTInstances;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceStatus;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;


/**
 * MDT 인스턴스를 시작하는 CLI 명령({@code instance}).
 * <p>
 * 시작 대상 지정은 다음 두 가지 모드 중 정확히 하나를 사용한다.
 * <ul>
 *   <li>인자로 하나 이상의 MDTInstance ID를 나열한다. (예: {@code instance a b c})
 *       {@code --recursive}/{@code -r}은 이 모드에서만 사용할 수 있다.</li>
 *   <li>{@code --all}/{@code -a}을 지정하여 실행 중이 아닌 모든 인스턴스를 시작한다.
 *       이 경우 ID 인자는 생략하며, {@code --recursive}와 함께 쓸 수 없다.</li>
 * </ul>
 * 위 규칙을 위반하면 {@link CommandLine.ParameterException}이 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "instance",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Start an MDTInstance."
)
public class StartMDTInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StartMDTInstanceCommand.class);

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 검증 실패 시 {@link CommandLine}을
	 * 얻어 {@link CommandLine.ParameterException}을 발생시키는 데 사용한다.
	 * 직접 인스턴스화 등 picocli 파싱을 거치지 않은 경우 {@code null}일 수 있다.
	 */
	@Spec private CommandSpec m_spec;

	/**
	 * 시작할 MDTInstance ID 목록. {@code --all}이 지정되지 않은 경우 하나 이상을 지정해야 한다.
	 */
	@Parameters(index="0..*", paramLabel="id",
				description="MDTInstance ids to start. Required unless --all is specified.")
	private List<String> m_instanceIdList = new ArrayList<>();

	/**
	 * 인스턴스가 RUNNING 상태가 될 때까지 상태를 폴링하는 주기. 기본값 1초.
	 */
	@Option(names={"--poll"}, paramLabel="duration", defaultValue = "1s",
			description="Status polling interval (e.g. \"1s\", \"500ms\", default: ${DEFAULT-VALUE})")
	private Duration m_pollingInterval = Duration.ofSeconds(1);

	/**
	 * 인스턴스가 RUNNING 상태가 될 때까지 기다리는 최대 시간. {@code null}이면 무한 대기.
	 */
	@Option(names={"--timeout"}, paramLabel="duration",
			description="Status sampling timeout (e.g. \"30s\", \"1m\", default: none)")
	private Duration m_timeout = null;

	/**
	 * 시작 요청 후 RUNNING 상태가 될 때까지 기다리지 않고 즉시 반환할지 여부.
	 */
	@Option(names={"--nowait"}, description="Do not wait until the instance reaches RUNNING state")
	private boolean m_nowait = false;

	/**
	 * 실행 중이 아닌 모든 MDTInstance를 시작할지 여부. {@code true}이면 ID 인자는 지정할 수 없다.
	 */
	@Option(names={"--all", "-a"},
			description="Start all non-running MDTInstances (cannot be combined with MDTInstance ids)")
	private boolean m_startAll = false;

	/**
	 * 인스턴스를 동시에 시작하기 위한 스레드 풀 크기. 기본값 1.
	 */
	@Option(names={"--nthreads", "-n"}, defaultValue = "1",
			description="Thread pool size (default: ${DEFAULT-VALUE})")
	private int m_nthreads = 1;

	/**
	 * 대상 인스턴스가 의존하는 인스턴스도 재귀적으로 시작할지 여부.
	 */
	@Option(names={"--recursive", "-r"},
			description="Start all dependent instances recursively (requires MDTInstance ids)")
	private boolean m_recursive = false;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception picocli 구성 또는 명령 실행 중 발생한 예외.
	 */
	public static void main(String... args) throws Exception {
		main(new StartMDTInstanceCommand(), args);
	}

	/**
	 * 이 명령의 로거를 {@code StartMDTInstanceCommand} 이름으로 초기화하는 생성자.
	 */
	public StartMDTInstanceCommand() {
		setLogger(s_logger);
	}

	/**
	 * {@link Runnable#run()} 진입점.
	 * <p>
	 * {@link #validateStartTargets()}로 시작 대상 검증을 먼저 수행한 뒤, 부모 클래스의 부트스트랩
	 * 절차({@link AbstractMDTCommand#run()})를 호출한다. 검증 실패 시 발생하는 예외는
	 * {@link #validateStartTargets()}의 문서를 참조한다.
	 */
	@Override
	public void run() {
		validateStartTargets();
		super.run();
	}

	/**
	 * 부모 클래스가 접속을 완료한 뒤 호출되는 명령 본체. {@link StartMDTInstances}를 빌드해
	 * 실제 시작 작업을 수행한다.
	 *
	 * @param mdt 접속이 완료된 {@link MDTManager}.
	 * @throws Exception 시작 작업 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
//		StartMDTInstances.builder()
//						.mdtInstanceManager(mdt.getInstanceManager())
//						.instanceIdList(m_instanceIdList)
//						.pollingInterval(m_pollingInterval)
//						.timeout(m_timeout)
//						.nowait(m_nowait)
//						.startAll(m_startAll)
//						.nthreads(m_nthreads)
//						.recursive(m_recursive)
//						.build()
//						.run();
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		List<HttpMDTInstanceClient> instList = new ArrayList<>();
		if ( m_startAll ) {
			FStream.from(manager.getInstanceAll())
					.filter(inst -> inst.getStatus() != MDTInstanceStatus.RUNNING)
					.forEach(instList::add);
		}
		else {
			for ( String id: m_instanceIdList ) {
				try {
					HttpMDTInstanceClient inst = manager.getInstance(id);
					instList.add(inst);
				}
				catch ( ResourceNotFoundException e ) {
					System.out.println("No such MDTInstance: " + id);
				}
			}
		}
		Duration pollInterval = m_nowait ? null : m_pollingInterval;
		StartInstances.newInstance()
						.addInstanceAll(instList)
						.pollInterval(pollInterval)
						.startTimeout(m_timeout)
						.concurrency(m_nthreads)
						.recursive(m_recursive)
						.statusListener(StartMDTInstanceCommand::printInstanceStatus)
						.run();
	}

	/**
	 * 시작 대상 지정이 다음 규칙을 만족하는지 확인한다.
	 * <ul>
	 *   <li>ID 목록과 {@code --all}을 동시에 지정할 수 없다.</li>
	 *   <li>ID 목록과 {@code --all} 중 적어도 하나는 지정되어야 한다.</li>
	 *   <li>{@code --recursive}는 ID 목록이 지정된 경우에만 사용할 수 있다.
	 *       ({@code --all}과 함께 쓸 수 없으며, ID 없이도 쓸 수 없다.)</li>
	 * </ul>
	 *
	 * @throws CommandLine.ParameterException 위 규칙 중 하나라도 위반된 경우.
	 */
	private void validateStartTargets() {
		boolean hasInstanceIds = !m_instanceIdList.isEmpty();
		if ( m_startAll && hasInstanceIds ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Specify either MDTInstance ids or --all, not both.");
		}
		if ( m_recursive && !hasInstanceIds ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"--recursive requires one or more MDTInstance ids; it cannot be combined with --all.");
		}
		if ( !m_startAll && !hasInstanceIds ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Specify at least one MDTInstance id or use --all.");
		}
	}

	/**
	 * 검증 메시지 발생용 {@link CommandLine}을 반환한다. picocli가 정상적으로 파싱한 경우
	 * 주입된 {@link #m_spec}에서 가져오고, 그렇지 않으면 새로 생성한 인스턴스를 사용한다.
	 *
	 * @return 이 명령에 연결된 {@link CommandLine}.
	 */
	private CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
	
	/**
	 * {@link #main(String...)} 예제에서 사용되는 상태 변경 리스너. 인스턴스의 현재 상태를
	 * 사람이 읽기 쉬운 메시지로 표준 출력에 출력한다.
	 *
	 * @param inst		상태가 변경된 인스턴스.
	 * @param status	새 상태.
	 */
	private static void printInstanceStatus(MDTInstance inst, MDTInstanceStatus status) {
		switch ( status ) {
			case STARTING:
				System.out.println("MDTInstance starting: " + inst.getId());
				break;
			case RUNNING:
				System.out.println("MDTInstance started: " + inst.getId());
				break;
			case FAILED:
				System.out.println("MDTInstance failed to start: " + inst.getId());
				break;
			default:
				System.out.println("MDTInstance failed to start: " + inst.getId()
									+ ", unexpected state: " + status);
				break;
		}
	}
}
