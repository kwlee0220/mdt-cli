package mdt.cli.stop;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.UnitUtils;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.StopMDTInstances;
import mdt.model.MDTManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * 실행 중인 MDTInstance를 정지시키는 CLI 명령.
 * <p>
 * 위치 인자로 정지 대상 MDTInstance id 목록을 받아 {@link StopMDTInstances} 작업을 구성/실행한다.
 * 옵션을 통해 정지 완료까지의 폴링 간격, 타임아웃, 대기 여부, 의존 인스턴스의 재귀 정지,
 * 일괄 정지({@code --all}), 작업 병렬도({@code --nthreads}) 등을 제어할 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "instance",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Stop a running MDT instance."
)
public class StopMDTInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StopMDTInstanceCommand.class);

	/** 정지 대상 MDTInstance id 목록. */
	@Parameters(index="0..*", paramLabel="ids", description="MDTInstance id list to stop.")
	private List<String> m_instanceIds;

	/** 인스턴스 상태 폴링 간격. 기본값 1초. */
	private Duration m_pollingInterval = UnitUtils.parseDuration("1s");
	/**
	 * {@code --poll} 옵션 세터.
	 * <p>
	 * {@link UnitUtils#parseDuration(String)}이 허용하는 형식(예: {@code "1s"}, {@code "500ms"})을 받는다.
	 *
	 * @param intervalStr 폴링 간격 문자열.
	 */
	@Option(names={"--poll"}, paramLabel="duration",
			description="Status polling interval (e.g. \"1s\", \"500ms\"")
	private void setPollingInterval(String intervalStr) {
		m_pollingInterval = UnitUtils.parseDuration(intervalStr);
	}

	/** 상태 샘플링 타임아웃. {@code null}이면 무제한 대기. */
	private Duration m_timeout = null;
	/**
	 * {@code --timeout} 옵션 세터.
	 * <p>
	 * {@link UnitUtils#parseDuration(String)}이 허용하는 형식(예: {@code "30s"}, {@code "1m"})을 받는다.
	 *
	 * @param toStr 타임아웃 문자열.
	 */
	@Option(names={"--timeout"}, paramLabel="duration",
			description="Status sampling timeout (e.g. \"30s\", \"1m\"). default: null")
	private void setTimeout(String toStr) {
		m_timeout = UnitUtils.parseDuration(toStr);
	}

	/** 모든 실행 중 MDTInstance를 일괄 정지할지 여부. */
	@Option(names={"--all", "-a"}, description="stop all running MDTInstances")
	private boolean m_stopAll;

	/** 정지 작업을 병렬로 수행할 스레드 풀 크기. 기본값 1. */
	@Option(names={"--nthreads", "-n"}, defaultValue = "1", description="Thread pool size (default: 1)")
	private int m_nthreads = 1;

	/** 의존 인스턴스까지 재귀적으로 정지할지 여부. {@code --all} 옵션이 지정되면 무시된다. */
	@Option(names={"--recursive", "-r"},
			description="stop all dependent instances recursively (ignored when --all is given)")
	private boolean m_recursive;

	/** 정지 완료까지 기다리지 않고 즉시 반환할지 여부. */
	@Option(names={"--nowait"}, description="Do not wait until the instance gets to stopped")
	private boolean m_nowait = false;

	/**
	 * CLI 진입점.
	 *
	 * @param args 커맨드라인 인자.
	 * @throws Exception 명령 실행 중 발생한 예외.
	 */
	public static final void main(String... args) throws Exception {
		main(new StopMDTInstanceCommand(), args);
	}

	/**
	 * 기본 로거를 설정하는 생성자.
	 */
	public StopMDTInstanceCommand() {
		setLogger(s_logger);
	}

	/**
	 * 옵션/인자로부터 {@link StopMDTInstances} 작업을 구성하여 실행한다.
	 *
	 * @param mdt 접속이 완료된 {@link MDTManager} 인스턴스.
	 * @throws IllegalArgumentException {@code --all} 옵션과 인스턴스 id 목록이 모두 지정되지 않은 경우.
	 * @throws Exception 정지 작업 수행 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		if ( !m_stopAll && (m_instanceIds == null || m_instanceIds.isEmpty()) ) {
			throw new IllegalArgumentException("No MDTInstance id specified: provide one or more ids, or use --all");
		}

		StopMDTInstances.builder()
						.mdtInstanceManager(mdt.getInstanceManager())
						.instanceIdList(m_instanceIds)
						.pollingInterval(m_pollingInterval)
						.timeout(m_timeout)
						.nowait(m_nowait)
						.stopAll(m_stopAll)
						.nthreads(m_nthreads)
						.recursive(m_recursive)
						.build()
						.run();
	}
}
