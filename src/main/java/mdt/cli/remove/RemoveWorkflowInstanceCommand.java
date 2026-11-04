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
	name = "workflow",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Remove the MDT Workflow instance."
)
public class RemoveWorkflowInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RemoveWorkflowInstanceCommand.class);

	@Parameters(index="0..*", paramLabel="workflow-name", description="Workflow instance names to remove")
	private List<String> m_wfNames = new ArrayList<>();
	
	@Option(names={"--model-filter"}, paramLabel="model-id", description="remove workflows of this model id (combinable with --glob)")
	private String m_modelFilter = null;
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern on workflow names (combinable with --model-filter)")
	private String m_glob = null;
	
	@Option(names={"--all", "-a"}, description="remove all workflows")
	private boolean m_removeAll;

	public static final void main(String... args) throws Exception {
		main(new RemoveWorkflowInstanceCommand(), args);
	}
	
	public RemoveWorkflowInstanceCommand() {
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
			svc.removeWorkflowAll();
		}
		else if ( m_glob != null || m_modelFilter != null ) {
			// 두 필터를 함께 주면 둘 다 만족하는 워크플로만 지운다.
			PathMatcher matcher = (m_glob != null)
								? FileSystems.getDefault().getPathMatcher("glob:" + m_glob) : null;
			FStream.from(svc.getWorkflowAll())
					.filter(wf -> matcher == null || matcher.matches(Paths.get(wf.getName())))
					.filter(wf -> m_modelFilter == null || m_modelFilter.equals(wf.getModelId()))
					.forEach(wf -> svc.removeWorkflow(wf.getName()));
		}
		else {
			for ( String wfId: m_wfNames ) {
				svc.removeWorkflow(wfId);
			}
		}
	}
	
	/**
	 * 삭제 대상은 이름 목록, {@code --all}, 필터({@code --glob}·{@code --model-filter}) 중 정확히 한 가지로만 지정한다.
	 */
	private void validateTargets() {
		boolean hasIds = !m_wfNames.isEmpty();
		boolean hasFilter = m_glob != null || m_modelFilter != null;
		int count = (hasIds ? 1 : 0) + (m_removeAll ? 1 : 0) + (hasFilter ? 1 : 0);
		if ( count == 0 ) {
			throw usageError("Specify workflow names, --all, or a filter (--glob, --model-filter).");
		}
		if ( count > 1 ) {
			throw usageError("Workflow names, --all and filters (--glob, --model-filter) cannot be combined.");
		}
	}
}
