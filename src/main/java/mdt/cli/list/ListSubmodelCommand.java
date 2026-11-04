package mdt.cli.list;

import java.io.PrintWriter;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import mdt.cli.AbstractMDTCommand;
import mdt.cli.RepeatOptions;
import mdt.cli.list.ListCommands.CSVCollector;
import mdt.cli.list.ListCommands.ListCollector;
import mdt.cli.list.ListCommands.TableCollector;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.model.MDTManager;
import mdt.model.ReferenceUtils;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ai.AI;
import mdt.model.sm.data.Data;
import mdt.model.sm.info.InformationModel;
import mdt.model.sm.simulation.Simulation;
import mdt.model.timeseries.TimeSeries;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodels",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all Submodels."
)
public class ListSubmodelCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListSubmodelCommand.class);

	@Option(names={"--filter", "-f"}, paramLabel="filter-expr", description="instance filter.")
	private String m_filter = null;
	
	@Mixin private ListOutputOptions m_outputOpts;

	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'simple' output only)")
	private String m_delimiter = ListCommands.DELIM;

	@Mixin private RepeatOptions m_repeatOpts;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;
	
	public ListSubmodelCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		m_repeatOpts.run(pw -> printOutput(manager, pw), "list AAS Submodels", m_verbose);
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
		table.setColumnWidth(2, 20, 50);
		table.setColumnWidth(5, 5, 70);

		table.addCell(" # ");
		table.addCell(" MDT_ID ");
		table.addCell(" ID ");
		table.addCell(" ID_SHORT ");
		table.addCell(" SEMANTIC_ID ");
		table.addCell(" ENDPOINT ");
		
		return collect(instances, new TableCollector(table));
	}

	private String collect(List<? extends MDTInstance> instances, ListCollector collector) {
		int seqNo = 1;
		for ( MDTInstance inst: instances ) {
			for ( SubmodelDescriptor smDesc: inst.getAASSubmodelDescriptorAll() ) {
				String[] cols = toColumns(seqNo, (HttpMDTInstanceClient)inst, smDesc);
				collector.collectLine(cols);
				
				++seqNo;
			}
		}
		
		return collector.getFinalString();
	}

	private String[] toColumns(int seqNo, HttpMDTInstanceClient inst, SubmodelDescriptor smDesc) {
		String[] cols = new String[] {
			String.format("%3d", seqNo), inst.getId(), smDesc.getId(), smDesc.getIdShort(),
			getSemanticIdString(smDesc), inst.getSubmodelServiceEndpoint(smDesc.getId())
		};
		return cols;
	}
	
	private String getSemanticIdString(SubmodelDescriptor smDesc) {
		String semanticId = ReferenceUtils.getSemanticIdStringOrNull(smDesc.getSemanticId());
		if ( semanticId == null ) {
			return "";
		}
		return switch ( semanticId ) {
			case Data.SEMANTIC_ID -> "Data";
			case InformationModel.SEMANTIC_ID -> "InformationModel";
			case Simulation.SEMANTIC_ID -> "Simulation";
			case AI.SEMANTIC_ID -> "AI";
			case TimeSeries.SEMANTIC_ID -> "TimeSeries";
			// 표시 이름을 모르는 semantic ID는 그대로 보여 준다(목록 조회 전체를 실패시키지 않는다).
			default -> semanticId;
		};
	}
}
