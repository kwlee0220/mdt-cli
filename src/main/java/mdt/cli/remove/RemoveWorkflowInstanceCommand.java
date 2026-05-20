package mdt.cli.remove;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.workflow.WorkflowManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
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
	description = "Remove the MDT Workflow instance."
)
public class RemoveWorkflowInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RemoveWorkflowInstanceCommand.class);

	@Parameters(index="0..*", paramLabel="ids", description="WorkflowDescriptor ids to remove")
	private List<String> m_wfIds;
	
	@Option(names={"--model-filter"}, paramLabel="model-id", description="target model id to remove")
	private String m_modelFilter = null;
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern to filter workflows.")
	private String m_glob = null;
	
	@Option(names={"--all", "-a"}, description="remove all WorkflowDescriptor")
	private boolean m_removeAll;

	public static final void main(String... args) throws Exception {
		main(new RemoveWorkflowInstanceCommand(), args);
	}
	
	public RemoveWorkflowInstanceCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager svc = mdt.getWorkflowManager();

		if ( m_removeAll ) {
			svc.removeWorkflowAll();
		}
		else if ( m_glob != null ) {
			String pattern = "glob:" + m_glob;
	        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);

	        FStream.from(svc.getWorkflowAll())
			        .filter(wf -> matcher.matches(Paths.get(wf.getName())))
			        .forEach(wf -> svc.removeWorkflow(wf.getName()));
		}
		else if ( m_modelFilter != null ) {
			FStream.from(svc.getWorkflowAll())
					.filter(wf -> wf.getModelId().equals(m_modelFilter))
					.forEach(wf -> svc.removeWorkflow(wf.getName()));
		}
		else {
			if ( m_wfIds == null || m_wfIds.isEmpty() ) {
				throw new IllegalArgumentException("no workflow id specified");
			}
			
			for ( String wfId: m_wfIds ) {
            	svc.removeWorkflow(wfId);
			}
		}
	}
}
