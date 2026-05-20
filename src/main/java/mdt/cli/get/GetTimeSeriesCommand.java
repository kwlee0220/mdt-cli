package mdt.cli.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.CellStyle.HorizontalAlign;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.UnitUtils;
import utils.func.Funcs;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.aas.DataTypes;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.ElementCollectionValue;
import mdt.model.sm.value.ElementValue;
import mdt.model.timeseries.TimeSeriesOperationReference;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
	
	private List<ColumnInfo> m_schema = null;
	
	@Parameters(index="0", paramLabel="id", description="MDTInstance id to show.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="submodel-idShort",
				description="Target TimeSeries submodel idShort")
	private String m_tsSubmodelIdShort;
	
	@Parameters(index = "2", paramLabel = "segments-idShort", defaultValue = "Tail",
				description = "Target Segments idShort")
	private String m_segementsId;
	
	@Option(names={"--header"}, description="display header row in CSV output")
	private boolean m_header = false;
	
	@Option(names={"--delim", "-d"}, paramLabel="delimiter", required=false,
			description="delimiter to use in CSV output (default: ',')")
	protected String m_delim = ",";
	
	@Option(names={"--start"}, paramLabel="datetime", required=false,
			description="start datetime to filter time series records")
	protected String m_startExpr = null;
	
	@Option(names = {"--end" }, paramLabel = "datetime", required = false,
			description = "end datetime to filter time series records")
	protected String m_endExpr = null;
	
	private static record ColumnInfo(String name, CellStyle cellStype) { };
	
	public GetTimeSeriesCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		m_schema = loadSchema(manager);
		
		if ( !m_segementsId.equalsIgnoreCase("range") ) {
			String elmRef = String.format("%s:%s:Segments.%s.Records",
											m_instanceId, m_tsSubmodelIdShort, m_segementsId);
			ElementReference smeRef = ElementReferences.parseExpr(elmRef);
			run(manager, smeRef);
		}
		else {
			Instant end = Instant.now();
			if ( m_endExpr != null ) {
				end = DataTypes.DATE_TIME.parseValueString(m_endExpr);
			}
			
			Instant start = null;
			if ( m_startExpr != null ) {
				if ( m_startExpr.startsWith("-") ) {
					Duration period = UnitUtils.parseDuration(m_startExpr.substring(1));
					start = end.minus(period);
				}
				else {
					start = DataTypes.DATE_TIME.parseValueString(m_startExpr);
					if ( start.compareTo(end) > 0 ) {
						throw new IllegalArgumentException("start time is earlier than end time: start="
															+ start + ", end=" + end);
					}
				}
			}
			else {
				throw new IllegalArgumentException("start time is not specified");
			}

			MDTElementReference opRef = ElementReferences.parseExpr(String.format("%s:%s:ReadRecords",
																			m_instanceId, m_tsSubmodelIdShort));
			TimeSeriesOperationReference readRecords = new TimeSeriesOperationReference(opRef, start, end);
			readRecords.activate(manager);
			run(manager, readRecords);
		}
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
				toDisplayValue(smeRef.readValue());
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
		Table table = new Table(m_schema.size());
		
		FStream.from(m_schema)
				.forEach(col -> table.addCell(" " + col.name + " ", HEADER_STYLE));

		ElementCollectionValue rows = (ElementCollectionValue)ref.readValue();
		FStream.from(rows.getFieldMap().values())
				.forEach(row -> addRow(table, (ElementCollectionValue)row));
		pw.println(table.render());
	}
	
	private void addRow(Table table, ElementCollectionValue row) {
		FStream.from(m_schema)
				.forEach(info -> {
					ElementValue colValue = row.getField(info.name);
					String str = (colValue != null) ? colValue.toDisplayString() : null;
					table.addCell(" " + str + " ", info.cellStype());
				});
		
	}
	
	private List<ColumnInfo> loadSchema(MDTInstanceManager manager) throws IOException {
		MDTElementReference metaElmRef = ElementReferences.parseExpr(
											String.format("%s:%s:Metadata.Record", m_instanceId, m_tsSubmodelIdShort));
		metaElmRef.activate(manager);
		SubmodelElementCollection recordMeta = metaElmRef.readCollection();
		
		return FStream.from(recordMeta.getValue())
							.map(field -> new ColumnInfo(field.getIdShort(), createCellStyle(field)))
							.toList();
	}
	private CellStyle createCellStyle(SubmodelElement cell) {
		if ( cell instanceof Property prop ) {
			switch ( prop.getValueType() ) {
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
		else {
			throw new IllegalArgumentException("Unsupported cell type: " + cell);
		}
	}
}
