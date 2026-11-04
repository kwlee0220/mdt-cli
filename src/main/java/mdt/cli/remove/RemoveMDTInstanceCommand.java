package mdt.cli.remove;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.model.InvalidResourceStatusException;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceStatus;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "instance",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Remove the MDT instance."
)
public class RemoveMDTInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RemoveMDTInstanceCommand.class);
	
	
	@Parameters(index="0..*", paramLabel="instance-id",
				description="MDTInstance ids to unregister. Required unless --all is specified.")
	private List<String> m_instanceIds = new ArrayList<>();
	
	@Option(names={"--all", "-a"},
			description="remove all MDTInstances (cannot be combined with MDTInstance ids)")
	private boolean m_removeAll;
	
	@Option(names={"--force", "-f"}, description="force to remove MDTInstances (even though they are running)")
	private boolean m_force;
	
	@Option(names={"--nthreads", "-n"}, paramLabel="count", defaultValue = "8",
			description="Thread pool size (default: ${DEFAULT-VALUE})")
	private int m_nthreads = 8;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;
	
	/** '--force'로 실행 중인 인스턴스를 정지할 때 정지 완료를 확인하는 간격. */
	private static final Duration STOP_POLL_INTERVAL = Duration.ofSeconds(1);

	public static final void main(String... args) throws Exception {
		main(new RemoveMDTInstanceCommand(), args);
	}

	public RemoveMDTInstanceCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void validate() {
		validateRemoveTargets();
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		if ( m_removeAll ) {
			if ( m_force ) {
				// 실행 중인 인스턴스가 모두 정지된 뒤에 제거해야 하므로, 정지 완료까지 기다린다.
				// 하나라도 정지하지 못했으면 전체 삭제를 하지 않는다.
				List<MDTInstance> running = manager.getInstanceAllByFilter("instance.status = 'RUNNING'");
				if ( !runConcurrently(running, this::stopIfRunning) ) {
					err().println("not removing all MDTInstances: some running instances could not be stopped");
					markFailed();
					return;
				}
			}
			manager.removeInstanceAll();
		}
		else {
			runConcurrently(m_instanceIds, instId -> removeInstance(manager, instId));
		}
	}
	
	/**
	 * 주어진 대상마다 {@code task}를 스레드 풀에서 실행하고 모두 끝날 때까지 기다린다.
	 * <p>
	 * {@code task}는 성공 여부를 돌려주며, 실패할 때는 자신의 오류를 직접 출력하고 {@link #markFailed()}로 기록한다.
	 * 그래도 빠져나온 예외는 여기서 출력하고 실패로 기록한다.
	 *
	 * @return	모든 대상이 성공했으면 {@code true}. 하나라도 실패했거나 기다리는 중 인터럽트되면 {@code false}.
	 */
	private <T> boolean runConcurrently(List<T> targets, Predicate<T> task) {
		if ( targets.isEmpty() ) {
			return true;
		}
		
		AtomicBoolean allSucceeded = new AtomicBoolean(true);
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(m_nthreads, targets.size()));
		try {
			for ( T target: targets ) {
				executor.submit(() -> {
					try {
						if ( !task.test(target) ) {
							allSucceeded.set(false);
						}
					}
					catch ( RuntimeException e ) {
						err().printf("failed to process MDTInstance: %s: cause %s%n", target, e);
						allSucceeded.set(false);
						markFailed();
					}
				});
			}
		}
		finally {
			executor.shutdown();
			try {
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			}
			catch ( InterruptedException e ) {
				executor.shutdownNow();
				allSucceeded.set(false);
				markFailed();
				Thread.currentThread().interrupt();
			}
		}
		return allSucceeded.get();
	}
	
	private boolean removeInstance(MDTInstanceManager manager, String instId) {
		try {
			if ( m_force ) {
				// 없는 인스턴스는 여기서 ResourceNotFoundException이 발생한다.
				MDTInstance inst = manager.getInstance(instId);
				if ( !stopIfRunning(inst) ) {
					return false;
				}
			}
			manager.removeInstance(instId);
			return true;
		}
		catch ( Exception e ) {
			err().printf("failed to remove MDTInstance: %s: cause %s%n", instId, e);
			markFailed();
			return false;
		}
	}
	
	/**
	 * 인스턴스가 실행 중이면 정지시키고 정지가 끝날 때까지 기다린다.
	 *
	 * @param inst	대상 인스턴스.
	 * @return	제거할 수 있는 상태(실행 중이 아님)가 되었으면 {@code true}. 정지에 실패했으면 오류를 출력하고
	 * 			실패로 기록한 뒤 {@code false}.
	 */
	private boolean stopIfRunning(MDTInstance inst) {
		try {
			if ( inst.isRunning() ) {
				MDTInstanceStatus status = inst.stop(STOP_POLL_INTERVAL, null);
				if ( status == MDTInstanceStatus.RUNNING ) {
					err().printf("failed to stop MDTInstance: %s, status=%s%n", inst.getId(), status);
					markFailed();
					return false;
				}
			}
			return true;
		}
		catch ( InvalidResourceStatusException e ) {
			// 확인과 정지 요청 사이에 이미 정지된 경우.
			return true;
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			markFailed();
			return false;
		}
		catch ( Exception e ) {
			err().printf("failed to stop MDTInstance: %s: cause %s%n", inst.getId(), e);
			markFailed();
			return false;
		}
	}
	
	private void validateRemoveTargets() {
		boolean hasInstanceIds = !m_instanceIds.isEmpty();
		if ( m_removeAll && hasInstanceIds ) {
			throw usageError("Specify either MDTInstance ids or --all, not both.");
		}
		if ( !m_removeAll && !hasInstanceIds ) {
			throw usageError("Specify at least one MDTInstance id or use --all.");
		}
		if ( m_nthreads < 1 ) {
			throw usageError("--nthreads must be greater than 0: " + m_nthreads);
		}
	}

}