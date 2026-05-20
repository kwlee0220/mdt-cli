package mdt.cli.stop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.workflow.WorkflowManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "workflow",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Stop an MDT Workflow."
)
public class StopWorkflowCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StopWorkflowCommand.class);
	
	@Parameters(index="0", paramLabel="wf-id", description="Workflow instance id to stop")
	private String m_wfId;

	public static final void main(String... args) throws Exception {
		main(new StopWorkflowCommand(), args);
	}
	
	public StopWorkflowCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();
		
		wfMgr.stopWorkflow(m_wfId);
	}
}
