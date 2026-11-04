package mdt.cli.get;

import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;
import utils.func.Nullables;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.workflow.WorkflowManager;
import mdt.workflow.WorkflowModel;
import mdt.workflow.model.TaskDescriptor;

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
	description = "Get an MDT Workflow model information.",
	subcommands = {
		GetWorkflowScriptCommand.class
	}
)
public class GetWorkflowModelCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetWorkflowModelCommand.class);
	
	@Parameters(index="0", paramLabel="model-id", description="Workflow model id to get")
	private String m_wfModelId;
	
	/** {@code --output} 값. */
	enum OutputType { table, json };
	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private OutputType m_output = OutputType.table;

	public static final void main(String... args) throws Exception {
		main(new GetWorkflowModelCommand(), args);
	}
	
	public GetWorkflowModelCommand() {
		setLogger(s_logger);
	}
	
	public String getWorkflowModelId() {
		return m_wfModelId;
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfMgr = mdt.getWorkflowManager();
		
		WorkflowModel wfModel = wfMgr.getWorkflowModel(m_wfModelId);
		switch ( m_output ) {
			case table:
				displayAsTable(wfModel);
				break;
			case json:
				displayAsJson(wfModel);
				break;
			default:
				throw new AssertionError("unknown output: " + m_output);
		}
	}
	
	private void displayAsTable(WorkflowModel wfModel) {
		Table table = new Table(2);

		table.addCell(" FIELD "); table.addCell(" VALUE");
		table.addCell(" ID "); table.addCell(" " + wfModel.getId());
		table.addCell(" NAME "); table.addCell(" " + wfModel.getName());
		table.addCell(" DEESCRIPTION "); table.addCell(" " + Nullables.orElse(wfModel.getDescription(), ""));
		
		FStream.from(wfModel.getTaskDescriptors()).zipWithIndex().forEach(tup -> {
			TaskDescriptor task = tup.value();
			
			table.addCell(String.format(" TASK[%02d] ", tup.index()));
			table.addCell(" " + task.toSignatureString());
		});
		
		out().println(table.render());
	}
	
	private void displayAsJson(WorkflowModel wfModel) {
		out().println(wfModel.toJsonString());
	}
}
