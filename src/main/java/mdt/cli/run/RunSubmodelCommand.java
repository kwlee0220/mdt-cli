package mdt.cli.run;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.model.MDTManager;
import mdt.model.ModelValidationException;
import mdt.model.SubmodelService;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultElementReference;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.model.sm.ref.DefaultSubmodelReference.ByIdShortSubmodelReference;
import mdt.task.builtin.AASOperationTask;
import mdt.task.builtin.AbstractTaskCommand;
import mdt.workflow.model.TaskDescriptor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodel",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "run an AI/Simulation Submodel."
)
public class RunSubmodelCommand extends AbstractTaskCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RunSubmodelCommand.class);
	private static final String DEFAULT_POLL_INTERVAL = "1s";
	
	@Parameters(index="0", paramLabel="id", description="MDTInstance id.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="submodel-idShort", description="Target AI/Simulation submodel idShort")
	private String m_submodelIdShort;

	@Option(names={"--poll"}, paramLabel="duration", description="Status polling interval (e.g. \"1s\", \"500ms\"")
	private String m_pollInterval = DEFAULT_POLL_INTERVAL;

	@Option(names={"--showResults"}, description="show output/inoutput operation variables")
	private boolean m_showResults = false;
	
	public RunSubmodelCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		Instant started = Instant.now();

		TaskDescriptor descriptor = new TaskDescriptor();
		descriptor.setId(m_submodelIdShort);
		descriptor.setType(AASOperationTask.class.getName());
		
		loadTaskDescriptor(descriptor);
		
		DefaultSubmodelReference smRef = ByIdShortSubmodelReference.ofIdShort(m_instanceId, m_submodelIdShort);
		smRef.activate(manager);
		descriptor.setSubmodelRef(smRef);
		
		DefaultElementReference opElmRef = DefaultElementReference.newInstance(descriptor.getSubmodelRef(),
																				"Operation");
		opElmRef.activate(manager);
		
		descriptor.addOption(AASOperationTask.OPTION_OPERATION, opElmRef.toStringExpr());
		descriptor.addOption(AASOperationTask.OPTION_POLL_INTERVAL, m_pollInterval);
		descriptor.addOption(AASOperationTask.OPTION_TIMEOUT, m_timeout);
		
		AASOperationTask aasOpTask = new AASOperationTask(descriptor);
		Map<String,SubmodelElement> outputs = aasOpTask.run(manager);
		if ( m_showResults ) {
			for ( Map.Entry<String, SubmodelElement> e : outputs.entrySet() ) {
				System.out.printf("%s: %s%n", e.getKey(), e.getValue());
			}
		}
		
		Duration elapsed = Duration.between(started, Instant.now());
		getLogger().info("Submodel: {}:{}, elapsedTime={}", m_instanceId, m_submodelIdShort, elapsed);
	}

//	private void loadOperationSubmodel(MDTInstanceManager manager, TaskDescriptor descriptor)
//		throws ModelValidationException {
//		DefaultSubmodelReference smRef = ByIdShortSubmodelReference.ofIdShort(m_instanceId, m_submodelIdShort);
//		smRef.activate(manager);
//		descriptor.setSubmodelRef(smRef);
//		
//		SubmodelService svc = smRef.get();
//		Submodel metadata = svc.getSubmodel(Modifier.METADATA);
//		
//		descriptor.setId(metadata.getIdShort());
//        descriptor.addLabel(TaskUtils.LABEL_MDT_OPERATION, smRef.toStringExpr());
//	}

	public static void main(String... args) throws Exception {
		main(new RunSubmodelCommand(), args);
		System.exit(0);
	}
}
