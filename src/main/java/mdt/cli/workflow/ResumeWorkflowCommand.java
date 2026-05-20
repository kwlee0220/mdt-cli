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
	name = "resume",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Resume an MDT Workflow."
)
public class ResumeWorkflowCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ResumeWorkflowCommand.class);
	
	@Parameters(index="0", paramLabel="workflow-name", description="Workflow model id to resume")
	private String m_wfName;

	public static final void main(String... args) throws Exception {
		main(new ResumeWorkflowCommand(), args);
	}
	
	public ResumeWorkflowCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();
		
		Workflow wf = wfMgr.resumeWorkflow(m_wfName);
		System.out.println(wf);
	}
}
