package mdt.cli.get;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.client.workflow.HttpWorkflowManager;
import mdt.model.MDTManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "script",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get an MDT Workflow script."
)
public class GetWorkflowScriptCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetWorkflowScriptCommand.class);
	
	@ParentCommand
	private GetWorkflowModelCommand m_parent;
	
	@Option(names={"--mdt-url"}, paramLabel="url", required=false,
			description="URL to the MDTPlatform")
	private String m_mdtUrl = null;
	
	@Option(names={"--mdt-client-docker"}, paramLabel="image-id", required=false,
			description="docker image of the MDTClient")
	private String m_clientDockerImage = null;

	public static final void main(String... args) throws Exception {
		main(new GetWorkflowScriptCommand(), args);
	}
	
	public GetWorkflowScriptCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpWorkflowManager wfMgr = (HttpWorkflowManager)mdt.getWorkflowManager();
		
		String script = wfMgr.getWorkflowScript(m_parent.getWorkflowModelId());
		System.out.println(script);
	}
}
