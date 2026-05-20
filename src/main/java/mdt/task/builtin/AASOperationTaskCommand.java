package mdt.task.builtin;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.run.RunSubmodelCommandOld;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultElementReference;
import mdt.workflow.model.TaskDescriptor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "aas",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "AAS-Operation-based Task execution command."
)
public class AASOperationTaskCommand extends AbstractTaskCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(AASOperationTaskCommand.class);
	private static final String DEFAULT_POLL_INTERVAL = "1s";
	
	@ParentCommand
	private RunSubmodelCommandOld m_parentCmd;

	@Option(names={"--poll"}, paramLabel="duration", description="Status polling interval (e.g. \"1s\", \"500ms\"")
	private String m_pollInterval = DEFAULT_POLL_INTERVAL;
	
	public AASOperationTaskCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		Instant started = Instant.now();

		TaskDescriptor descriptor = new TaskDescriptor();
		descriptor.setType(AASOperationTask.class.getName());
		
        // 해당 연산 Submodel을 읽어서 주요 TaskDescriptor 정보를 설정한다.
		m_parentCmd.loadOperationSubmodel(manager, descriptor);
		
		loadTaskDescriptor(descriptor);
		
		DefaultElementReference opElmRef = DefaultElementReference.newInstance(descriptor.getSubmodelRef(),
																				"Operation");
		opElmRef.activate(manager);
		
		descriptor.addOption(AASOperationTask.OPTION_OPERATION, opElmRef.toStringExpr());
		descriptor.addOption(AASOperationTask.OPTION_POLL_INTERVAL, m_pollInterval);
		descriptor.addOption(AASOperationTask.OPTION_TIMEOUT, m_timeout);
		
		AASOperationTask aasOpTask = new AASOperationTask(descriptor);
		aasOpTask.run(mdt.getInstanceManager());
		
		Duration elapsed = Duration.between(started, Instant.now());
		getLogger().info("AASOperationTask: ref={}, elapsedTime={}", opElmRef, elapsed);
	}

	public static void main(String... args) throws Exception {
		main(new AASOperationTaskCommand(), args);
		System.exit(0);
	}
}
