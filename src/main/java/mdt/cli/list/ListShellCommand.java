package mdt.cli.list;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import utils.UnitUtils;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.PeriodicRefreshingConsole;
import mdt.cli.list.ListCommands.CSVCollector;
import mdt.cli.list.ListCommands.ListCollector;
import mdt.cli.list.ListCommands.TableCollector;
import mdt.model.InvalidResourceStatusException;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTSubmodelDescriptor;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "shells",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all AssetAdministrationShells."
)
public class ListShellCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListShellCommand.class);

	@Option(names={"--filter", "-f"}, paramLabel="filter-expr", description="instance filter.")
	private String m_filter = null;
	
	@Option(names={"--table", "-t"}, description="'table' format output")
	private boolean m_table = false; 

	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'simple' output only)")
	private String m_delimiter = ListCommands.DELIM;

	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\")")
	private String m_repeat = null;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;
	
	public ListShellCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		if ( m_repeat == null ) {
			try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
				printOutput(manager, pw);
			}
			return;
		}
		else {
			Duration repeatInterval = UnitUtils.parseDuration(m_repeat);
			PeriodicRefreshingConsole pwriter = new PeriodicRefreshingConsole(repeatInterval) {
				@Override
				protected void print(PrintWriter pw) {
					try {
						printOutput(manager, pw);
					}
					catch ( Exception ignored ) {
						pw.println("failed to list AAS Shells: cause=" + ignored);
					}
				}
			};
			pwriter.setVerbose(m_verbose);
			pwriter.run();
		}
	}
	
	private void printOutput(MDTInstanceManager manager, PrintWriter pw) {
		List<? extends MDTInstance> instances = (m_filter != null)
												? manager.getInstanceAllByFilter(m_filter)
												: manager.getInstanceAll();
		
		String listStr = (m_table) ? buildTableString(instances) + System.lineSeparator()
									: buildCsvString(instances);
		pw.print(listStr);
	}

	private String buildCsvString(List<? extends MDTInstance> instances) {
		return collect(instances, new CSVCollector(m_delimiter));
	}

	private String buildTableString(List<? extends MDTInstance> instances) {
		Table table = new Table(6);
		table.setColumnWidth(1, 20, 70);
		table.setColumnWidth(5, 10, 50);

		table.addCell(" # ");
		table.addCell(" ID ");
		table.addCell(" ID_SHORT ");
		table.addCell(" GLOBAL_ASSET_ID ");
		table.addCell(" ASSET_TYPE ");
		table.addCell(" SUBMODELS ");
		
		return collect(instances, new TableCollector(table));
	}
	
	private String collect(List<? extends MDTInstance> instances, ListCollector collector) {
		int seqNo = 1;
		for ( MDTInstance inst: instances ) {
			try {
				AssetAdministrationShell aas = inst.getAssetAdministrationShellService()
													.getAssetAdministrationShell();
				Object[] cols = toColumns(seqNo, aas, inst);
				collector.collectLine(cols);
			}
			catch ( InvalidResourceStatusException expected ) {
				AssetAdministrationShellDescriptor aasDesc = inst.getAASShellDescriptor();
				Object[] cols = toColumns(seqNo, aasDesc, inst);
				collector.collectLine(cols);
			}
			++seqNo;
		}
		
		return collector.getFinalString();
	}
	
	private String[] toColumns(int seqNo, AssetAdministrationShell aas, MDTInstance inst) {
		String smIdCsv = FStream.from(inst.getMDTSubmodelDescriptorAll())
								.map(MDTSubmodelDescriptor::getIdShort)
								.join(", ");
		
		return new String[] {
			String.format("%3d", seqNo),
			aas.getId(),										// ID
			aas.getIdShort(),									// ID_SHORT
			aas.getAssetInformation().getGlobalAssetId(),		// GLOBAL_ASSET_ID
			aas.getAssetInformation().getAssetType(),			// ASSET_TYPE
			smIdCsv,											// SUBMODELS
		};
	}
	
	private String[] toColumns(int seqNo, AssetAdministrationShellDescriptor aasDesc, MDTInstance inst) {
		String smIdCsv = FStream.from(aasDesc.getSubmodelDescriptors())
								.map(SubmodelDescriptor::getIdShort)
								.join(", ");
		return new String[] {
			String.format("%3d", seqNo),
			aasDesc.getId(),				// ID
			aasDesc.getIdShort(),			// ID_SHORT
			aasDesc.getGlobalAssetId(),		// GLOBAL_ASSET_ID
			aasDesc.getAssetType(),			// ASSET_TYPE
			smIdCsv,						// SUBMODELS
		};
	}
}
