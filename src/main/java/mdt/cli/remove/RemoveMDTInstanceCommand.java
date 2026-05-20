package mdt.cli.remove;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import utils.LoggerNameBuilder;
import utils.func.Unchecked;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;

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
	private static final Logger s_logger = LoggerNameBuilder.from(RemoveMDTInstanceCommand.class)
															.dropSuffix(2)
															.append("unregister.mdt_instances")
															.getLogger();
	
	@Parameters(index="0..*", paramLabel="ids", description="MDTInstance ids to unregister")
	private List<String> m_instanceIds;
	
	@Option(names={"--all", "-a"}, description="remove all MDTInstances")
	private boolean m_removeAll;
	
	@Option(names={"--force", "-f"}, description="force to remove MDTInstances (eventhough they are running)")
	private boolean m_force;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new RemoveMDTInstanceCommand(), args);
	}

	public RemoveMDTInstanceCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		if ( m_removeAll ) {
			if ( m_force ) {
				manager.getInstanceAllByFilter("instance.status = 'RUNNING'")
						.parallelStream()
						.forEach(inst -> {
							Unchecked.runOrIgnore(() -> inst.stop(null, null));
						});
			}
			manager.removeInstanceAll();
		}
		else {
			ExecutorService exector = Executors.newFixedThreadPool(8);
			try {
				for ( String instId: m_instanceIds ) {
					exector.submit(() -> deleteInstance(manager, instId));
				}
			}
			finally {
				exector.shutdown();
				try {
					exector.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	private void deleteInstance(MDTInstanceManager manager, String instId) {
		if ( m_force ) {
			MDTInstance inst = manager.getInstance(instId);
			Unchecked.runOrIgnore(() -> inst.stop(null, null));
		}
		try {
			manager.removeInstance(instId);
		}
		catch ( Exception e ) {
			System.out.printf("failed to remove MDTInstance: %s: cause %s%n", instId, e);
		}
	}
}