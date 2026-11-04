package mdt.cli.remove;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
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

	@Parameters(index="0..*", paramLabel="model-id", description="Workflow model ids to remove")
	private List<String> m_modelIds = new ArrayList<>();
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern on workflow model ids")
	private String m_glob = null;
	
	@Option(names={"--all", "-a"}, description="remove all workflow models")
	private boolean m_removeAll;

	public static final void main(String... args) throws Exception {
		main(new RemoveWorkflowModelCommand(), args);
	}
	
	public RemoveWorkflowModelCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void validate() {
		validateTargets();
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager svc = mdt.getWorkflowManager();
		if ( m_removeAll ) {
			svc.removeWorkflowModelAll();
		}
		else if ( m_glob != null ) {
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + m_glob);
			FStream.from(svc.getWorkflowModelAll())
					.filter(model -> matcher.matches(Paths.get(model.getId())))
					.forEach(model -> svc.removeWorkflowModel(model.getId()));
		}
		else {
			for ( String modelId: m_modelIds ) {
				svc.removeWorkflowModel(modelId);
			}
		}
	}
	
	/**
	 * 삭제 대상은 id 목록, {@code --all}, {@code --glob} 중 정확히 한 가지로만 지정한다.
	 */
	private void validateTargets() {
		int count = (!m_modelIds.isEmpty() ? 1 : 0) + (m_removeAll ? 1 : 0) + (m_glob != null ? 1 : 0);
		if ( count == 0 ) {
			throw usageError("Specify workflow model ids, --all, or --glob.");
		}
		if ( count > 1 ) {
			throw usageError("Workflow model ids, --all and --glob cannot be combined.");
		}
	}
}
