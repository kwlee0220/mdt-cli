package mdt.cli.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.workflow.Workflow;
import mdt.workflow.WorkflowManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "suspend",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Suspend an MDT Workflow."
)
public class SuspendWorkflowCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(SuspendWorkflowCommand.class);
	
	@Parameters(index="0", paramLabel="workflow-name", description="Workflow model id to suspend")
	private String m_wfName;

	public static final void main(String... args) throws Exception {
		main(new SuspendWorkflowCommand(), args);
	}
	
	public SuspendWorkflowCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();
		
		Workflow wf = wfMgr.suspendWorkflow(m_wfName);
		System.out.println(wf);
	}
}
