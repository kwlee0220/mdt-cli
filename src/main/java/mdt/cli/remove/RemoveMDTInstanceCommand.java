package mdt.cli.remove;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;

import utils.LoggerNameBuilder;

import mdt.cli.AbstractMDTCommand;
import mdt.model.InvalidResourceStatusException;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceStatus;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;


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
	private static final Logger s_logger = LoggerNameBuilder.from(RemoveMDTInstanceCommand.class)
															.dropSuffix(2)
															.append("unregister.mdt_instances")
															.getLogger();
	
	@Spec private CommandSpec m_spec;
	
	@Parameters(index="0..*", paramLabel="ids",
				description="MDTInstance ids to unregister. Required unless --all is specified.")
	private List<String> m_instanceIds = new ArrayList<>();
	
	@Option(names={"--all", "-a"},
			description="remove all MDTInstances (cannot be combined with MDTInstance ids)")
	private boolean m_removeAll;
	
	@Option(names={"--force", "-f"}, description="force to remove MDTInstances (eventhough they are running)")
	private boolean m_force;
	
	@Option(names={"--nthreads", "-n"}, defaultValue = "8",
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
	public void run() {
		// MDTManager에 접속하기 전에 대상 지정을 검사한다.
		validateRemoveTargets();
		super.run();
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		if ( m_removeAll ) {
			if ( m_force ) {
				// 실행 중인 인스턴스가 모두 정지된 뒤에 제거해야 하므로, 정지 완료까지 기다린다.
				List<MDTInstance> running = manager.getInstanceAllByFilter("instance.status = 'RUNNING'");
				runConcurrently(running, this::stopIfRunning);
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
	 * {@code task}는 자신의 오류를 직접 출력하고 {@link #markFailed()}로 기록해야 한다. 그래도 빠져나온
	 * 예외는 여기서 출력하고 실패로 기록한다.
	 */
	private <T> void runConcurrently(List<T> targets, Consumer<T> task) {
		if ( targets.isEmpty() ) {
			return;
		}
		
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(m_nthreads, targets.size()));
		try {
			for ( T target: targets ) {
				executor.submit(() -> {
					try {
						task.accept(target);
					}
					catch ( RuntimeException e ) {
						System.out.printf("failed to process MDTInstance: %s: cause %s%n", target, e);
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
				markFailed();
				Thread.currentThread().interrupt();
			}
		}
	}
	
	private void removeInstance(MDTInstanceManager manager, String instId) {
		try {
			if ( m_force ) {
				// 없는 인스턴스는 여기서 ResourceNotFoundException이 발생한다.
				MDTInstance inst = manager.getInstance(instId);
				if ( !stopIfRunning(inst) ) {
					return;
				}
			}
			manager.removeInstance(instId);
		}
		catch ( Exception e ) {
			System.out.printf("failed to remove MDTInstance: %s: cause %s%n", instId, e);
			markFailed();
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
					System.out.printf("failed to stop MDTInstance: %s, status=%s%n", inst.getId(), status);
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
			System.out.printf("failed to stop MDTInstance: %s: cause %s%n", inst.getId(), e);
			markFailed();
			return false;
		}
	}
	
	private void validateRemoveTargets() {
		boolean hasInstanceIds = !m_instanceIds.isEmpty();
		if ( m_removeAll && hasInstanceIds ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Specify either MDTInstance ids or --all, not both.");
		}
		if ( !m_removeAll && !hasInstanceIds ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"Specify at least one MDTInstance id or use --all.");
		}
		if ( m_nthreads < 1 ) {
			throw new CommandLine.ParameterException(getCommandLine(),
					"--nthreads must be greater than 0: " + m_nthreads);
		}
	}

	private CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
}