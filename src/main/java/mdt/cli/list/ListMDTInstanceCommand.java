package mdt.cli.list;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyle;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import utils.InternalException;
import utils.StopWatch;
import utils.UnitUtils;
import utils.Utilities;
import utils.func.Optionals;
import utils.http.RESTfulIOException;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.PeriodicRefreshingConsole;
import mdt.cli.list.Nodes.InstanceNode;
import mdt.cli.list.Nodes.RootNode;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.ReferenceUtils;
import mdt.model.ResourceNotFoundException;
import mdt.model.instance.InstanceDescriptor;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceStatus;

/**
 * 등록된 MDT 인스턴스 전체를 다양한 형식으로 나열하는 CLI 명령({@code instances}).
 * <p>
 * 출력 형식은 {@code --output} 옵션으로 선택한다.
 * <ul>
 *   <li>{@code CSV} (기본값): 구분자로 구분된 한 줄짜리 레코드</li>
 *   <li>{@code TABLE}: 사람이 읽기 좋은 테이블</li>
 *   <li>{@code TREE}: 컴포넌트 의존 관계를 트리로 표현</li>
 *   <li>{@code JSON}: JSON 직렬화</li>
 * </ul>
 * {@code --long}을 지정하면 자산 타입/ID, 서브모델 요약, 엔드포인트 등 자세한 정보가 함께 출력된다.
 * {@code --repeat}을 지정하면 주기적으로 화면을 갱신한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "instances",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all MDTInstances."
)
public class ListMDTInstanceCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListMDTInstanceCommand.class);

	/** {@code json} 출력에 사용하는 공용 AAS JSON 직렬기. */
	private static final JsonSerializer JSON_SERIALIZER = new JsonSerializer();

	/**
	 * 지원하는 출력 형식. picocli의 case-insensitive enum 변환과 결합되어
	 * 사용자는 {@code csv}/{@code CSV} 등으로 입력할 수 있다.
	 */
	public enum OutputType {
		/** 구분자로 구분된 한 줄짜리 레코드. */
		CSV,
		/** 사람이 읽기 좋은 테이블 형식. */
		TABLE,
		/** 컴포넌트 의존 관계를 트리 형태로 표현. */
		TREE,
		/** JSON 직렬화 출력. */
		JSON
	}

	/** 인스턴스 필터 표현식. {@code null}이면 모든 인스턴스를 대상으로 한다. */
	@Option(names={"--filter", "-f"}, paramLabel="filter-expr", description="instance filter.")
	private String m_filter = null;

	/** 출력 형식. 기본값은 {@link OutputType#CSV}. */
	@Option(names={"--output", "-o"}, paramLabel="type", required=false,
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private OutputType m_output = OutputType.CSV;

	/**
	 * {@code --output=TABLE}을 강제하는 단축 옵션.
	 * <p>
	 * 인자 순서와 무관하게 {@code --output}보다 우선 적용된다. 즉, {@code --output=JSON -t}와
	 * {@code -t --output=JSON} 모두 최종 출력 형식은 {@link OutputType#TABLE}이 된다.
	 * 우선 적용은 {@link #run(MDTManager)} 진입 시점에 수행된다.
	 */
	@Option(names={"--table", "-t"},
			description="force 'TABLE' format output. Takes precedence over --output regardless of order.")
	private boolean m_forceTable = false;

	/**
	 * 주기적 갱신 간격(예: {@code "1s"}, {@code "500ms"}). {@code null}이면 단발 실행.
	 */
	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\")")
	private String m_repeat = null;

	/** 자세한 정보 출력 여부. {@code CSV}/{@code TABLE} 출력에만 유효. */
	@Option(names={"--long", "-l"}, description="show detailed information. valid only for CSV and TABLE output")
	private boolean m_long = false;

	/** {@code CSV} 출력에서 사용할 필드 구분자. 기본값은 {@link ListCommands#DELIM}. */
	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for CSV output only)")
	private String m_delimiter = ListCommands.DELIM;

	/** {@code JSON} 출력 시 보기 좋게 들여쓰기 여부. */
	@Option(names={"--pretty"}, description="pretty print (for JSON output only)")
	private boolean m_prettyPrint = false;

	/** {@code TREE} 출력에서 실행 중 인스턴스의 엔드포인트를 함께 표시할지 여부. */
	@Option(names={"--show-endpoint"}, description="show endpoint for running MDT instances. valid only for TREE output")
	private boolean m_showEndpoint = false;

	/** 주기적 갱신 모드에서의 verbose 출력 여부. */
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	/**
	 * 이 명령의 로거를 {@code ListMDTInstanceCommand} 이름으로 초기화하는 생성자.
	 */
	public ListMDTInstanceCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령의 본체. 인스턴스 목록을 조회하여 선택된 형식으로 출력한다.
	 * <p>
	 * {@code --repeat}이 지정되지 않은 경우 한 번 출력하고 경과 시간을 함께 표시한다. 지정된
	 * 경우 {@link PeriodicRefreshingConsole}을 통해 주기적으로 화면을 갱신한다.
	 *
	 * @param mdt 부모 클래스가 접속을 완료한 {@link MDTManager}.
	 * @throws Exception 출력 또는 주기 실행 중 발생한 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		// --table/-t는 인자 순서와 무관하게 --output보다 우선한다.
		if ( m_forceTable ) {
			m_output = OutputType.TABLE;
		}

		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();

		if ( m_repeat == null ) {
			StopWatch watch = StopWatch.start();
			try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
				printOutput(manager, pw);
				watch.stop();

				double elapsed = watch.getElapsedInFloatingSeconds();
				pw.printf("elapsed: %.3f%n", elapsed);
			}
			return;
		}
		else {
			Duration repeatInterval = UnitUtils.parseDuration(m_repeat);
			PeriodicRefreshingConsole pwriter = new PeriodicRefreshingConsole(repeatInterval) {
				@Override
				protected void print(PrintWriter pw) throws InterruptedException {
					try {
						printOutput(manager, pw);
					}
					catch ( InterruptedException e ) {
						Thread.currentThread().interrupt();
						throw e;
					}
					catch ( Exception e ) {
						pw.println("failed to list MDTInstances: cause=" + e);
					}
				}
			};
			pwriter.setVerbose(m_verbose);
			pwriter.run();
		}
	}

	/**
	 * 인스턴스 목록을 조회한 뒤 {@code --output}과 {@code --long} 조합에 따라 적절한 렌더링을 수행한다.
	 *
	 * @param manager 인스턴스 조회에 사용할 매니저.
	 * @param pw      출력 대상 writer.
	 * @throws InterruptedException 주기적 갱신 중 IO 인터럽트가 발생한 경우.
	 */
	private void printOutput(HttpMDTInstanceManager manager, PrintWriter pw) throws InterruptedException {
		try {
			List<HttpMDTInstanceClient> instances;
			if ( m_filter != null ) {
				instances = manager.getInstanceAllByFilter(m_filter);
			}
			else {
				instances = manager.getInstanceAll();
			}

			if ( m_long ) {
				switch ( m_output ) {
					case CSV -> printLongCsv(instances, pw);
					case TABLE -> printLongTable(instances, pw);
					case TREE -> printTree(instances, pw);
					case JSON -> printJson(instances, pw);
					default -> throw new IllegalStateException("unsupported output type: " + m_output);
				}
			}
			else {
				switch ( m_output ) {
					case CSV -> printShortCsv(instances, pw);
					case TABLE -> printShortTable(instances, pw);
					case TREE -> printTree(instances, pw);
					case JSON -> printJson(instances, pw);
					default -> throw new IllegalStateException("unsupported output type: " + m_output);
				}
			}
		}
		catch ( ResourceNotFoundException e ) {
			pw.println("failed to list MDTInstances: " + Optionals.getOrElse(e.getCause(), e));
		}
		catch ( RESTfulIOException e ) {
			Throwable cause = e.getCause();
			if ( cause instanceof InterruptedIOException ) {
				Thread.currentThread().interrupt();
				throw new InterruptedException("interrupted while listing MDTInstances");
			}
			else {
				pw.println("failed to list MDTInstances: " + Optionals.getOrElse(cause, e));
			}
		}
	}

	/**
	 * 순번/ID/상태 세 컬럼만 갖는 CSV 형식으로 출력한다.
	 */
	private void printShortCsv(List<? extends MDTInstance> instances, PrintWriter pw) {
		int seqNo = 1;
		for ( MDTInstance inst : instances ) {
			pw.append(""+seqNo).append(m_delimiter);
			pw.append(inst.getId()).append(m_delimiter);
			pw.append(inst.getStatus().toString());
			pw.println();
			++seqNo;
		}
	}

	/**
	 * 순번/ID/상태 세 컬럼만 갖는 테이블 형식으로 출력한다.
	 */
	private void printShortTable(List<? extends MDTInstance> instances, PrintWriter pw) {
		Table table = new Table(3);
		table.addCell(" # ");
		table.addCell(" INSTANCE ");
		table.addCell(" STATUS ");

		int seqNo = 1;
		for ( MDTInstance inst : instances ) {
			table.addCell(String.format("%3d", seqNo));
			table.addCell(inst.getId());
			table.addCell(""+inst.getStatus());
			++seqNo;
		}
		pw.println(table.render());
	}

	/**
	 * 자산 타입/ID, 서브모델 요약, 엔드포인트까지 포함한 자세한 CSV를 출력한다.
	 */
	private void printLongCsv(List<? extends MDTInstance> instances, PrintWriter pw) {
		int seqNo = 1;
		for ( MDTInstance inst : instances ) {
			Object[] cells = toLongColumns(seqNo, inst, "%d");
			String line = FStream.of(cells).map(Object::toString).join(m_delimiter);
			pw.println(line);
			++seqNo;
		}
	}

	/**
	 * 자산 타입/ID, 서브모델 요약, 엔드포인트까지 포함한 자세한 테이블을 출력한다.
	 */
	private void printLongTable(List<? extends MDTInstance> instances, PrintWriter pw) {
		Table table = new Table(7);
		table.setColumnWidth(3, 10, 50);
		table.setColumnWidth(4, 10, 35);
		table.addCell(" # ");
		table.addCell(" INSTANCE ");
		table.addCell(" ASSET_TYPE ");
		table.addCell(" ASSET_ID ");
		table.addCell(" SUB_MODELS ");
		table.addCell(" STATUS ");
		table.addCell(" ENDPOINT ");

		int seqNo = 1;
		for ( MDTInstance inst : instances ) {
			Object[] cells = toLongColumns(seqNo, inst, "%3d");
			FStream.of(cells).map(Object::toString).forEach(table::addCell);
			++seqNo;
		}
		pw.println(table.render());
	}

	/**
	 * 각 인스턴스의 {@link InstanceDescriptor}를 JSON 배열로 직렬화하여 출력한다.
	 * {@code --pretty}가 설정된 경우 들여쓰기를 적용한다.
	 *
	 * @throws InternalException JSON 직렬화에 실패한 경우.
	 */
	private void printJson(List<HttpMDTInstanceClient> instances, PrintWriter pw) {
		try {
			List<InstanceDescriptor> modelList = FStream.from(instances)
														.map(inst -> inst.getInstanceDescriptor())
														.toList();

			JsonNode modelsNode = JSON_SERIALIZER.toNode(modelList);

			String json;
			if ( m_prettyPrint ) {
				json = MDTModelSerDe.MAPPER.writerWithDefaultPrettyPrinter()
											.writeValueAsString(modelsNode) + System.lineSeparator();
			}
			else {
				json = MDTModelSerDe.MAPPER.writeValueAsString(modelsNode) + System.lineSeparator();
			}
			pw.println(json);
		}
		catch ( IOException | RuntimeException e ) {
			throw new InternalException("failed to serialize MDTInstances to JSON", e);
		}
	}

	/**
	 * 실행 중인 인스턴스의 컴포넌트 의존 관계를 반영하여 트리 형태로 출력한다.
	 * <p>
	 * 모든 인스턴스를 일단 루트 자식으로 추가한 뒤, 실행 중인 부모 인스턴스의 컴포넌트로 참조되는
	 * (그리고 자신도 실행 중인) 자식들을 부모 아래로 옮긴다. 트리 스타일은 OS에 따라 다르게 적용된다.
	 */
	private void printTree(List<? extends MDTInstance> instances, PrintWriter pw) {
		Nodes.s_showEndpoint = m_showEndpoint;

		// take a snapshot
		Map<String,InstanceNode> nodes = FStream.from(instances)
												.castSafely(HttpMDTInstanceClient.class)
												.map(InstanceNode::new)
												.tagKey(InstanceNode::getId)
												.toMap();

		// 초기 구조를 구축한다.
		RootNode root = new RootNode();
		List<InstanceNode> runningNodes = Lists.newArrayList();
		for ( InstanceNode node: nodes.values() ) {
			if ( node.getStatus() == MDTInstanceStatus.RUNNING ) {
				runningNodes.add(node);
			}
			root.addChild(node);
		}

		FStream.from(runningNodes)
				.forEach(node -> {
					for ( MDTInstance comp: node.getInstance().getComponentInstanceAll() ) {
						InstanceNode depNode = nodes.get(comp.getId());
						if ( depNode != null && depNode.getStatus() == MDTInstanceStatus.RUNNING ) {
							node.addChild(depNode);
							root.removeChild(depNode);
						}
					}
				});

		TreeOptions opts = new TreeOptions();
		TreeStyle style = Utilities.isWindowsOS() ? TreeStyles.WIN_TREE : TreeStyles.UNICODE_ROUNDED;
		opts.setStyle(style);
		opts.setMaxDepth(5);
		pw.print(TextTree.newInstance(opts).render(root));
	}

	/**
	 * {@code --long} 모드의 CSV/테이블에서 사용할 컬럼 값들을 계산한다.
	 * <p>
	 * 서브모델 의미 ID를 그룹화하여 {@code Info}/{@code Data}는 그대로 표시하고, 나머지는
	 * {@code 그룹명(개수)} 형식으로 요약한다.
	 *
	 * @param seqNo    출력 순번.
	 * @param instance 대상 인스턴스.
	 * @param format   순번 포맷 문자열(예: {@code "%d"}, {@code "%3d"}).
	 * @return 한 행을 구성하는 컬럼 값들의 배열.
	 */
	private Object[] toLongColumns(int seqNo, MDTInstance instance, String format) {
		List<String> outputs = Lists.newArrayList();
		FStream.from(instance.getMDTSubmodelDescriptorAll())
				.map(isdesc -> ReferenceUtils.getShortSubmodelSemanticId(isdesc.getSemanticId()))
				.tagKey(n -> n)
				.groupByKey()
				.switcher()
				.ifCase("Info").consume(grp -> outputs.add("Info"))
				.ifCase("Data").consume(grp -> outputs.add("Data"))
				.otherwise().forEach((k, grp) -> outputs.add(String.format("%s(%d)", k, grp.size())));

		String submodelIdCsv = FStream.from(outputs).join(',');

		String serviceEndpoint = ObjectUtils.defaultIfNull(instance.getServiceEndpoint(), "");
		return new Object[] {
			String.format(format, seqNo),
			instance.getId(),
			Optionals.getOrElse(instance.getAssetType(), ""),
			Optionals.getOrElse(instance.getGlobalAssetId(), ""),
			submodelIdCsv,
			instance.getStatus(),
			serviceEndpoint
		};
	}
}
