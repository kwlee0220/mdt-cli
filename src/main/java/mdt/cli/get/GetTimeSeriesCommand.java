package mdt.cli.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.CellStyle.HorizontalAlign;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import utils.UnitUtils;
import utils.func.Funcs;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.timeseries.ReadLastRecordsReference;
import mdt.model.sm.ref.timeseries.TimeSeriesRange.Anchor;
import mdt.model.sm.value.ElementCollectionValue;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "timeseries",
	aliases =  { "ts" },
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get parameter information of MDTInstance."
)
public class GetTimeSeriesCommand extends AbstractGetElementCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetTimeSeriesCommand.class);
	
	@Parameters(index="0", paramLabel="id", description="MDTInstance id to show.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="submodel-idShort",
				description="Target TimeSeries submodel idShort")
	private String m_tsSubmodelIdShort;
	
	@Option(names={"--header"}, description="display header row in CSV output")
	private boolean m_header = false;
	
	@Option(names={"--delim", "-d"}, paramLabel="delimiter", required=false,
			description="delimiter to use in CSV output (default: ',')")
	protected String m_delim = ",";
	
	@Option(names={"--last"}, paramLabel="period(@anchor)?", required=false,
			description="display last count/period of time series records (e.g. 5, 1d, 2h, 30m)")
	private String m_last = null;
	
	@Option(names={"--start"}, paramLabel="datetime", required=false,
			description="start datetime to filter time series records")
	private String m_startExpr = null;
	
	@Option(names = {"--end" }, paramLabel = "datetime", required = false,
			description = "end datetime to filter time series records")
	private String m_endExpr = null;
	
	@Option(names = {"--columns" }, paramLabel = "columns", required = false,
			description = "selected columns for time series records")
	private String m_columns = null;
	
	private static record CellInfo(String name, CellStyle cellStype) { };
	
	public GetTimeSeriesCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		DefaultSubmodelReference smRef = DefaultSubmodelReference.ofIdShort(m_instanceId, m_tsSubmodelIdShort);
		smRef.activate(manager);
		
		var builder = ReadLastRecordsReference.builder()
												.submodelReference(smRef);
		if ( m_last != null ) {
			String[] parts = m_last.split("@");
			try {
				Duration period = UnitUtils.parseDuration(parts[0]);
				if ( parts.length == 2 ) {
					if ( parts[1].equalsIgnoreCase("now") ) {
						builder.last(period, Anchor.NOW);
					}
					else {
						builder.last(period, Anchor.LATEST);
					}
				}
				else {
					builder.last(period, Anchor.LATEST);
				}
			}
			catch ( IllegalArgumentException e ) {
				int count = Integer.parseInt(parts[0]);
				builder.last(count);
			}
		}
		
		if ( m_columns != null ) {
			builder.columns(Arrays.asList(m_columns.split(",")));
		}
		
		ReadLastRecordsReference tsRef = builder.build();
		tsRef.activate(manager);
		
		run(manager, tsRef);
	}

	@Override
	protected void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception {
		switch ( m_output ) {
			case "csv":
				printAsCsv((ElementCollectionValue)smeRef.readValue(), pw);
				break;
			case "table":
				printTable(smeRef, pw);
				break;
			case "tree":
				pw.print(toDisplayTree(smeRef.read(), TREE_OPTS));
				break;
			case "json":
				pw.print(toDisplayJson(smeRef.read()));
				break;
			case "value":
				pw.print(toDisplayValue(smeRef.readValue()));
				break;
			default:
				throw new IllegalArgumentException("Invalid output type: " + m_output);
		};
	}
	
	protected void printAsCsv(ElementCollectionValue value, PrintWriter pw) {
		if ( m_header ) {
			ElementCollectionValue first = (ElementCollectionValue)Funcs.getFirst(value.getFieldMap().values());
			String header = FStream.from(first.getFieldMap().keySet()).join(m_delim);
			pw.println(header);
		}
		
		KeyValueFStream.from(value.getFieldMap())
						.values()
						.cast(ElementCollectionValue.class)
						.map(this::toRowString)
						.forEach(pw::println);
	}
	
	private String toRowString(ElementCollectionValue rowValue) {
		return FStream.from(rowValue.getFieldMap().values())
						.map(ElementValue::toDisplayString)
						.join(m_delim);
		
	}
	
	private static final CellStyle HEADER_STYLE = new CellStyle(HorizontalAlign.CENTER);
	private void printTable(ElementReference ref, PrintWriter pw) throws IOException {
		SubmodelElementCollection smec = (SubmodelElementCollection)ref.read();
		
		List<SubmodelElement> rows = smec.getValue();
		if ( rows.isEmpty() ) {
			return;
		}
		
		SubmodelElementCollection first = (SubmodelElementCollection)rows.get(0);
		List<CellInfo> cellInfos = loadCellInfos(first);
		
		Table table = new Table(cellInfos.size());
		
		FStream.from(cellInfos)
				.forEach(col -> table.addCell(" " + col.name + " ", HEADER_STYLE));

		FStream.from(rows)
				.map(ElementValues::getValue)
				.forEach(row -> addRow(table, cellInfos, (ElementCollectionValue)row));
		pw.println(table.render());
	}
	
	private void addRow(Table table, List<CellInfo> cellInfos, ElementCollectionValue row) {
		FStream.from(cellInfos)
				.forEach(info -> {
					ElementValue colValue = row.getField(info.name);
					String str = (colValue != null) ? colValue.toDisplayString() : null;
					table.addCell(" " + str + " ", info.cellStype());
				});
		
	}
	
	private List<CellInfo> loadCellInfos(SubmodelElementCollection proto) throws IOException {
		return FStream.from(proto.getValue())
							.cast(Property.class)
							.map(prop -> new CellInfo(prop.getIdShort(), createCellStyle(prop.getValueType())))
							.toList();
	}
	private CellStyle createCellStyle(DataTypeDefXsd colType) {
		switch ( colType ) {
			case STRING:
				return new CellStyle(HorizontalAlign.LEFT);
			case INTEGER: case INT:
			case DOUBLE:
			case FLOAT:
			case LONG:
			case SHORT:
			case DECIMAL:
				return new CellStyle(HorizontalAlign.RIGHT);
			default:
				return new CellStyle(HorizontalAlign.CENTER);
		}
	}
}
