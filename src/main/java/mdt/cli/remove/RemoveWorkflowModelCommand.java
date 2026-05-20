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
	name = "wfmodel",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Remove the MDT Workflow model."
)
public class RemoveWorkflowModelCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RemoveWorkflowModelCommand.class);

	@Parameters(index="0..*", paramLabel="ids", description="Workflow model ids to remove")
	private List<String> m_modelIds;
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern to filter workflows.")
	private String m_glob = null;
	
	@Option(names={"--all", "-a"}, description="remove all WorkflowDescriptor")
	private boolean m_removeAll;

	public static final void main(String... args) throws Exception {
		main(new RemoveWorkflowModelCommand(), args);
	}
	
	public RemoveWorkflowModelCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager svc = mdt.getWorkflowManager();

		if ( m_removeAll ) {
			svc.removeWorkflowModelAll();
		}
		else if ( m_glob != null ) {
			String pattern = "glob:" + m_glob;
	        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
	        
	        FStream.from(svc.getWorkflowModelAll())
			        .filter(wf -> matcher.matches(Paths.get(wf.getId())))
			        .forEach(wf -> svc.removeWorkflow(wf.getId()));
		}
		else {
			if ( m_modelIds == null || m_modelIds.isEmpty() ) {
				throw new IllegalArgumentException("no workflow template id specified");
			}
			
			for ( String modelId: m_modelIds ) {
                svc.removeWorkflowModel(modelId);
			}
		}
	}
}
