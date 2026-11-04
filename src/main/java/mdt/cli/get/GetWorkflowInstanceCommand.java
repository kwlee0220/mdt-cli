package mdt.cli.get;

import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.stream.FStream;
import utils.func.Nullables;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.workflow.NodeTask;
import mdt.workflow.Workflow;
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
	description = "Get an MDT Workflow instance.",
	subcommands = {
		GetWorkflowLogCommand.class
	}
)
public class GetWorkflowInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetWorkflowInstanceCommand.class);
	
	@Parameters(index="0", paramLabel="workflow-name", description="Workflow instance name to get")
	private String m_wfName;
	
	/** {@code --output} 값. */
	enum OutputType { table, json };
	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private OutputType m_output = OutputType.table;

	public static final void main(String... args) throws Exception {
		main(new GetWorkflowInstanceCommand(), args);
	}
	
	public GetWorkflowInstanceCommand() {
		setLogger(s_logger);
	}
	
	public String getWorkflowName() {
		return m_wfName;
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();
		
		Workflow workflow = wfMgr.getWorkflow(m_wfName);
		switch (m_output) {
			case table:
				displayAsTable(workflow);
				break;
			case json:
				displayAsJson(workflow);
				break;
			default:
				throw new AssertionError("unknown output: " + m_output);
		}
	}
	
	private void displayAsTable(Workflow workflow) {
		Table table = new Table(2);

		table.addCell(" FIELD "); table.addCell(" VALUE");
		table.addCell(" NAME "); table.addCell(" " + workflow.getName());
		table.addCell(" MODEL "); table.addCell(" " + workflow.getModelId());
		table.addCell(" STATUS "); table.addCell(" " + workflow.getStatus());
		table.addCell(" CREATION_TIME "); table.addCell(" " + workflow.getCreationTime());
		table.addCell(" START_TIME "); table.addCell(" " + Nullables.orElse(workflow.getStartTime(), ""));
		table.addCell(" FINISH_TIME "); table.addCell(" " + Nullables.orElse(workflow.getFinishTime(), ""));
		
		FStream.from(workflow.getTasks()).zipWithIndex().forEach(tup -> {
			NodeTask task = tup.value();
			
			table.addCell(String.format(" TASK[%02d] ", tup.index()));
//			String depListStr = FStream.from(task.getDependents())
//										.join(", ", "[", "]");
			String msg = String.format(" %s (%s)", task.getTaskId(), task.getStatus());
			table.addCell(msg);
		});
		
		out().println(table.render());
	}
	
	private void displayAsJson(Workflow workflow) throws JsonProcessingException {
		JsonMapper mapper = MDTModelSerDe.getJsonMapper();
		out().println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow));
	}
}
