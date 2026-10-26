package mdt.cli.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import utils.Instants;
import utils.UnitUtils;
import utils.func.Funcs;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.timeseries.TimeSeriesRange;
import mdt.model.sm.ref.timeseries.TimeSeriesRange.Anchor;
import mdt.model.sm.ref.timeseries.TimeSeriesRecordsReference;
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
	description = "Get time series records of MDTInstance."
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
			description="start datetime to filter time series records (e.g. 2024-01-01T09:00:00Z)")
	private String m_startExpr = null;

	@Option(names = {"--end" }, paramLabel = "datetime", required = false,
			description = "end datetime to filter time series records (e.g. 2024-01-01T09:00:00Z)")
	private String m_endExpr = null;

	@Option(names = {"--count" }, paramLabel = "count", required = false,
			description = "number of records to read from '--start' datetime in ascending order")
	private Integer m_count = null;
	
	@Option(names = {"--columns" }, paramLabel = "columns", required = false,
			description = "selected columns for time series records")
	private String m_columns = null;
	
	private static record CellInfo(String name, CellStyle cellStype) { };

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 사용자 친화적 오류 메시지를 생성하기 위해
	 * {@link CommandLine}을 얻을 때 사용한다.
	 */
	@Spec private CommandSpec m_spec;

	public GetTimeSeriesCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		// 원격 접근 이전에 옵션 조합을 먼저 검증한다.
		TimeSeriesRange range = buildRange();

		MDTInstanceManager manager = mdt.getInstanceManager();

		DefaultSubmodelReference smRef = DefaultSubmodelReference.ofIdShort(m_instanceId, m_tsSubmodelIdShort);
		smRef.activate(manager);

		var builder = TimeSeriesRecordsReference.builder()
												.submodelReference(smRef);
		if ( range != null ) {
			builder.range(range);
		}

		if ( m_columns != null ) {
			builder.columns(Arrays.asList(m_columns.split(",")));
		}

		TimeSeriesRecordsReference tsRef = builder.build();
		tsRef.activate(manager);
		
		run(manager, tsRef);
	}

	/**
	 * 조회 범위 관련 옵션들로부터 {@link TimeSeriesRange}를 생성한다.
	 * <p>
	 * 옵션 조합에 따라 다음과 같이 해석한다.
	 * <ul>
	 *   <li>{@code --last N} / {@code --last <기간>[@anchor]} &mdash; 마지막 N개 / 마지막 기간 구간
	 *   <li>{@code --start T --count N} &mdash; {@code T}(포함)부터 시간 오름차순으로 N개
	 *   <li>{@code --start T}, {@code --end T}, 또는 둘 다 &mdash; 절대 시간 범위(한쪽 개방 가능)
	 * </ul>
	 * {@code --last}는 나머지 옵션들과 함께 사용할 수 없고, {@code --count}는 {@code --start}를
	 * 필요로 하며 {@code --end}와 함께 사용할 수 없다.
	 *
	 * @return	생성된 조회 범위. 관련 옵션이 하나도 지정되지 않은 경우는 {@code null}(전체 조회).
	 * @throws CommandLine.ParameterException	옵션 조합이 잘못되었거나 옵션 값을 해석할 수 없는 경우.
	 */
	private TimeSeriesRange buildRange() {
		if ( m_last != null ) {
			if ( m_startExpr != null || m_endExpr != null || m_count != null ) {
				throw parameterError("'--last' cannot be used with '--start', '--end' or '--count'");
			}
			return parseLastRange(m_last);
		}

		if ( m_count != null ) {
			if ( m_startExpr == null ) {
				throw parameterError("'--count' requires '--start'");
			}
			if ( m_endExpr != null ) {
				throw parameterError("'--count' cannot be used with '--end'");
			}
			if ( m_count < 0 ) {
				throw parameterError("'--count' must be non-negative: " + m_count);
			}
			return TimeSeriesRange.since(parseDatetime("--start", m_startExpr), m_count);
		}

		if ( m_startExpr != null || m_endExpr != null ) {
			Instant from = (m_startExpr != null) ? parseDatetime("--start", m_startExpr) : null;
			Instant to = (m_endExpr != null) ? parseDatetime("--end", m_endExpr) : null;
			try {
				return TimeSeriesRange.between(from, to);
			}
			catch ( IllegalArgumentException e ) {
				throw parameterError(e.getMessage());
			}
		}

		return null;
	}

	/**
	 * {@code --last} 옵션 값을 {@link TimeSeriesRange}로 해석한다.
	 * <p>
	 * 접미사가 없는 정수({@code 5})는 '마지막 N개'를, 접미사가 붙은 기간({@code 30m}, {@code 1d})은
	 * '마지막 기간 구간'을 의미한다. 기간에는 {@code @now}/{@code @latest} 기준점을 덧붙일 수 있으며,
	 * 생략하면 {@link Anchor#LATEST}로 간주한다.
	 *
	 * @param lastExpr	{@code --last} 옵션 값.
	 * @return	생성된 조회 범위.
	 * @throws CommandLine.ParameterException	개수로도 기간으로도 해석할 수 없는 경우.
	 */
	private TimeSeriesRange parseLastRange(String lastExpr) {
		String[] parts = lastExpr.split("@");
		if ( parts.length > 2 ) {
			throw parameterError("invalid '--last' value: " + lastExpr);
		}

		try {
			Duration period = UnitUtils.parseDuration(parts[0]);
			Anchor anchor = (parts.length == 2) ? parseAnchor(parts[1]) : Anchor.LATEST;
			return TimeSeriesRange.last(period, anchor);
		}
		catch ( IllegalArgumentException expected ) {
			// 접미사가 없는 값은 기간으로 해석되지 않으므로 레코드 개수로 재해석한다.
			if ( parts.length == 2 ) {
				// anchor는 기간 범위에만 사용할 수 있다.
				throw parameterError("'@" + parts[1] + "' anchor cannot be used with a record count: " + lastExpr);
			}
			try {
				return TimeSeriesRange.length(Integer.parseInt(parts[0]));
			}
			catch ( NumberFormatException e ) {
				throw parameterError("invalid '--last' value: " + lastExpr);
			}
		}
	}

	/**
	 * {@code --last} 옵션의 기준점({@code @} 뒤) 표기를 {@link Anchor}로 해석한다.
	 *
	 * @param anchorExpr	기준점 표기. {@code now} 또는 {@code latest}(대소문자 무시).
	 * @return	해석된 기준점.
	 * @throws CommandLine.ParameterException	알 수 없는 기준점인 경우.
	 */
	private Anchor parseAnchor(String anchorExpr) {
		if ( anchorExpr.equalsIgnoreCase("now") ) {
			return Anchor.NOW;
		}
		else if ( anchorExpr.equalsIgnoreCase("latest") ) {
			return Anchor.LATEST;
		}
		else {
			throw parameterError("invalid '--last' anchor (candidates: now, latest): " + anchorExpr);
		}
	}

	/**
	 * 시각 옵션 값을 {@link Instant}로 해석한다.
	 *
	 * @param optionName	오류 메시지에 사용할 옵션 이름.
	 * @param datetimeExpr	해석할 시각 문자열.
	 * @return	해석된 시각.
	 * @throws CommandLine.ParameterException	시각 문자열을 해석할 수 없는 경우.
	 */
	private Instant parseDatetime(String optionName, String datetimeExpr) {
		try {
			return Instants.fromString(datetimeExpr);
		}
		catch ( DateTimeParseException e ) {
			throw parameterError(String.format("invalid '%s' datetime: %s", optionName, datetimeExpr));
		}
	}

	private CommandLine.ParameterException parameterError(String details) {
		return new CommandLine.ParameterException(m_spec.commandLine(), details);
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
			// 헤더는 첫번째 레코드의 필드 이름들로 구성하기 때문에, 조회된 레코드가 없는 경우는
			// 컬럼 이름을 알 수 없으므로 헤더를 출력하지 않는다.
			ElementCollectionValue first = (ElementCollectionValue)Funcs.getFirst(value.getFieldMap().values());
			if ( first != null ) {
				String header = FStream.from(first.getFieldMap().keySet()).join(m_delim);
				pw.println(header);
			}
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
