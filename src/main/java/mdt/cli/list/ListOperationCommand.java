package mdt.cli.list;

import java.io.PrintWriter;
import java.util.List;

import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.RepeatOptions;
import mdt.cli.list.ListCommands.CSVCollector;
import mdt.cli.list.ListCommands.ListCollector;
import mdt.cli.list.ListCommands.TableCollector;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTOperationDescriptor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "operations",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all operations (AI or Simulations) submodels."
)
public class ListOperationCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListOperationCommand.class);
	private static final String CLEAR_CONSOLE_CONTROL = "\033[2J\033[1;1H";

	@Option(names={"--filter", "-f"}, paramLabel="filter-expr", description="instance filter.")
	private String m_filter = null;
	
	@Mixin private ListOutputOptions m_outputOpts;

	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'simple' output only)")
	private String m_delimiter = ListCommands.DELIM;

	@Mixin private RepeatOptions m_repeatOpts;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;
	
	public ListOperationCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		m_repeatOpts.run(pw -> printOutput(manager, pw), "list Operations", m_verbose);
	}
	
	private void printOutput(MDTInstanceManager manager, PrintWriter pw) {
		List<? extends MDTInstance> instances = (m_filter != null)
												? manager.getInstanceAllByFilter(m_filter)
												: manager.getInstanceAll();
		
		String listStr = (m_outputOpts.isTable()) ? buildTableString(instances) + System.lineSeparator()
									: buildCsvString(instances);
		pw.print(listStr);
	}

	private String buildCsvString(List<? extends MDTInstance> instances) {
		return collect(instances, new CSVCollector(m_delimiter));
	}

	private String buildTableString(List<? extends MDTInstance> instances) {
		Table table = new Table(6);
		table.setColumnWidth(4, 20, 70);
		table.setColumnWidth(5, 10, 40);

		table.addCell(" # ");
		table.addCell(" MDT ");
		table.addCell(" OPERATION ");
		table.addCell(" TYPE ");
		table.addCell(" INPUTS ");
		table.addCell(" OUTPUTS ");
		
		return collect(instances, new TableCollector(table));
	}
	
	private String collect(List<? extends MDTInstance> instances, ListCollector collector) {
		int seqNo = 1;
		for ( MDTInstance inst: instances ) {
			for ( MDTOperationDescriptor opDesc: inst.getMDTOperationDescriptorAll() ) {
				String[] cols = toOperationColumns(seqNo, inst, opDesc);
				collector.collectLine(cols);
				++seqNo;
			}
		}
		
		return collector.getFinalString();
	}
	
	private String[] toOperationColumns(int seqNo, MDTInstance inst, MDTOperationDescriptor opDesc) {
		String inArgNameCsv = FStream.from(opDesc.getInputArguments())
					                .map(MDTOperationDescriptor.ArgumentDescriptor::getId)
					                .join(",");
		String outArgNameCsv = FStream.from(opDesc.getOutputArguments())
				                    .map(MDTOperationDescriptor.ArgumentDescriptor::getId)
                                    .join(",");
		return new String[] { String.format("%3d", seqNo), inst.getId(), opDesc.getId(), opDesc.getOperationType(),
								inArgNameCsv, outArgNameCsv };
	}
}
