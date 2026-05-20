package mdt.cli.get;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;

import org.barfuin.texttree.api.Node;
import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import utils.UnitUtils;
import utils.func.Optionals;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.PeriodicRefreshingConsole;
import mdt.model.InvalidResourceStatusException;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.model.SubmodelService;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.SubmodelUtils;
import mdt.model.sm.ai.AI;
import mdt.model.sm.data.ParameterCollection;
import mdt.model.sm.info.InformationModel;
import mdt.model.sm.simulation.Simulation;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;
import mdt.tree.node.data.ParameterCollectionNode;
import mdt.tree.node.info.MDTInfoNode;
import mdt.tree.node.op.OperationEntityNode;

/**
 * 지정된 MDTInstance의 MDT 모델 정보를 트리 형태로 출력하는 CLI 명령이다.
 * <p>
 * {@code mdt-info} 명령으로 실행되며, 명령행 옵션을 통해 다음 항목들을 선택적으로 표시할 수 있다.
 * <ul>
 *   <li>{@code --info, -i}: InformationModel Submodel의 {@code MDTInfo} 정보</li>
 *   <li>{@code --parameters, -p}: {@link ParameterCollection} 정보</li>
 *   <li>{@code --operations, -o}: AI 및 Simulation operation 목록</li>
 *   <li>{@code --compositions, -c}: TwinComposition의 CompositionItems 및 CompositionDependencies</li>
 * </ul>
 * 선택된 항목들은 단일 루트 노드 아래에 트리 구조로 렌더링되어 표준 출력으로 출력된다.
 * <p>
 * {@code --repeat, -r} 옵션이 주어지면 지정된 간격으로 화면을 갱신하며 정보를 반복적으로 출력한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "mdt-info",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get MDT model information for the instance."
)
public class GetMdtInfoCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetMdtInfoCommand.class);

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 사용자 친화적 오류 메시지를 생성하기 위해
	 * {@link CommandLine}을 얻을 때 사용한다.
	 */
	@Spec private CommandSpec m_spec;

	@Parameters(index="0", paramLabel="id", description="MDTInstance id to show.")
	private String m_instanceId;

	@Option(names={"--info", "-i"}, description="show MDTInfo")
	private boolean m_info = false;

	@Option(names={"--parameters", "-p"}, description="show parameters")
	private boolean m_parameters = false;

	@Option(names={"--operations", "-o"}, description="show operations (AI or Simulation)")
	private boolean m_operations = false;

	@Option(names={"--compositions", "-c"}, description="show compositions")
	private boolean m_compositions = false;

	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\")")
	private String m_repeat = null;

	@Option(names={"--verbose", "-v"}, description="verbose")
	private boolean m_verbose = false;

	/**
	 * 본 명령을 단독 실행 진입점으로 호출한다.
	 *
	 * @param args 명령행 인자
	 * @throws Exception 명령 실행 도중 발생한 모든 예외
	 */
	public static final void main(String... args) throws Exception {
		main(new GetMdtInfoCommand(), args);
	}

	/**
	 * 기본 생성자. 로거를 초기화한다.
	 */
	public GetMdtInfoCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령을 실제로 수행한다.
	 * <p>
	 * {@code --repeat} 옵션이 지정되지 않은 경우에는 한 번만 정보를 출력하고 종료한다.
	 * 지정된 경우에는 {@link PeriodicRefreshingConsole}을 이용해서 주어진 간격으로 화면을 갱신하며
	 * 출력을 반복한다. 반복 출력 중 인스턴스가 실행 중이 아니거나 정보를 가져오는데 실패한 경우에는
	 * 오류 메시지를 출력하고 다음 갱신을 기다린다.
	 *
	 * @param mdt {@link MDTManager} 인스턴스
	 * @throws Exception 명령 실행 도중 발생한 예외
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		if ( m_repeat == null ) {
			try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
				MDTInstance instance = manager.getInstance(m_instanceId);
				printOutput(instance, pw);
			}
			catch ( InvalidResourceStatusException expected ) {
				System.err.println("instance is not running: id=" + m_instanceId);
			}
			catch ( Exception e ) {
				System.err.printf("failed to get MDT model info: instance=%s, cause=%s%n", m_instanceId, e);
			}
			return;
		}

		Duration repeatInterval = UnitUtils.parseDuration(m_repeat);
		PeriodicRefreshingConsole pwriter = new PeriodicRefreshingConsole(repeatInterval) {
			@Override
			protected void print(PrintWriter pw) {
				try {
					MDTInstance instance = manager.getInstance(m_instanceId);
					printOutput(instance, pw);
				}
				catch ( InvalidResourceStatusException e ) {
					pw.println("instance is not running: id=" + m_instanceId);
				}
				catch ( ResourceNotFoundException e ) {
					pw.println("instance is not found: " + e.getMessage());
				}
				catch ( Exception e ) {
					pw.printf("failed to get MDT model info: instance=%s, cause=%s%n", m_instanceId, e);
				}
			}
		};
		pwriter.setVerbose(m_verbose);
		pwriter.run();
	}

	private static final int MAX_TREE_DEPTH = 5;
	private static final TreeOptions TREE_OPTS = new TreeOptions();
	static {
		TREE_OPTS.setStyle(TreeStyles.UNICODE_ROUNDED);
		TREE_OPTS.setMaxDepth(MAX_TREE_DEPTH);
	}

	private void printOutput(MDTInstance instance, PrintWriter pw) {
		boolean showInfo = m_info;
		boolean showParameters = m_parameters;
		boolean showOperations = m_operations;
		boolean showCompositions = m_compositions;
		if ( !showInfo && !showParameters && !showOperations && !showCompositions ) {
			showParameters = true;
		}

		List<DefaultNode> nodes = Lists.newArrayList();
		if ( showInfo ) {
			Optionals.accept(buildInfoNode(instance, pw), nodes::add);
		}
		if ( showParameters ) {
			try {
				nodes.add(buildParametersNode(instance));
			}
			catch ( ResourceNotFoundException e ) {
				throw new CommandLine.ParameterException(getCommandLine(),
														"No Data Submodel found in MDTInstance.");
			}
		}
		if ( showOperations ) {
			Optionals.accept(buildOperationsNode(instance), nodes::add);
		}
		if ( showCompositions ) {
			SubmodelService infoSvc = getInformationModelService(instance);
			if ( infoSvc == null ) {
				if ( m_verbose ) {
					pw.println("No InformationModel found.");
				}
			}
			else {
				Optionals.accept(buildCompositionSubNode(infoSvc, "TwinComposition.CompositionItems",
														"Components", "CompositionItems", pw),
								nodes::add);
				Optionals.accept(buildCompositionSubNode(infoSvc, "TwinComposition.CompositionDependencies",
														"Dependencies", "CompositionDependencies", pw),
								nodes::add);
			}
		}

		if ( nodes.isEmpty() ) {
			if ( m_verbose ) {
				pw.println("No information to display.");
			}
			return;
		}

		DefaultNode root = new DefaultNode("MDTInstance: " + instance.getId(), null, "") {
			@Override
			public Iterable<? extends Node> getChildren() {
				return nodes;
			}
		};
		root.setHideValue(true);
		String treeString = TextTree.newInstance(TREE_OPTS).render(root);
		pw.print(treeString);
	}
	
	private DefaultNode buildInfoNode(MDTInstance instance, PrintWriter pw) {
		SubmodelService infoSvc = getInformationModelService(instance);
		if ( infoSvc == null ) {
			if ( m_verbose ) {
				pw.println("No InformationModel found.");
			}
			return null;
		}
		try {
			SubmodelElement sme = infoSvc.getSubmodelElementByPath("MDTInfo");
			return MDTInfoNode.FACTORY.create(sme);
		}
		catch ( ResourceNotFoundException e ) {
			if ( m_verbose ) {
				pw.println("No MDTInfo found in InformationModel.");
			}

			return null;
		}
	}

	private SubmodelService getInformationModelService(MDTInstance instance) {
		return FStream.from(instance.getSubmodelServiceAllBySemanticId(InformationModel.SEMANTIC_ID))
						.findFirst()
						.getOrNull();
	}
	
	private DefaultNode buildParametersNode(MDTInstance instance) {
		ParameterCollection paramColl = instance.getParameterCollection();
		return new ParameterCollectionNode(paramColl);
	}
	
	private DefaultNode buildOperationsNode(MDTInstance instance) {
		List<OperationEntityNode> opNodes = getOperationNodeList(instance, AI.SEMANTIC_ID, "AI");
		opNodes.addAll(getOperationNodeList(instance, Simulation.SEMANTIC_ID, "Simulation"));
		if ( opNodes.isEmpty() ) {
			return null;
		}
		return new DefaultNode("Operations", null, "") {
			@Override
			public Iterable<? extends Node> getChildren() {
				return opNodes;
			}
		};
	}
	
	private List<OperationEntityNode> getOperationNodeList(MDTInstance instance, String semanticId, String tag) {
		return FStream.from(instance.getSubmodelServiceAllBySemanticId(semanticId))
						.map(svc -> {
							Submodel sm = svc.getSubmodel();
							SubmodelElement opElm = SubmodelUtils.traverse(sm, tag + "Info");
							OperationEntityNode node = new OperationEntityNode(sm.getIdShort(), opElm);
							node.setValueType(String.format(" (%s)", tag));
							return node;
						})
						.toList();
	}
	
	private DefaultNode buildCompositionSubNode(SubmodelService infoSvc, String path, String label,
												String missingName, PrintWriter pw) {
		try {
			SubmodelElement sme = infoSvc.getSubmodelElementByPath(path);
			return new DefaultNode(label, null, "") {
				@Override
				public Iterable<? extends Node> getChildren() {
					return FStream.from(((SubmodelElementList)sme).getValue())
							.map(item -> DefaultNodeFactories.create(item))
							.toList();
				}
			};
		}
		catch ( ResourceNotFoundException e ) {
			if ( m_verbose ) {
				pw.println("No " + missingName + " found in InformationModel.");
			}

			return null;
		}
	}

	/**
	 * 검증 메시지 발생용 {@link CommandLine}을 반환한다. picocli가 정상적으로 파싱한 경우 주입된
	 * {@link #m_spec}에서 가져오고, 그렇지 않으면 새로 생성한 인스턴스를 사용한다.
	 *
	 * @return 이 명령에 연결된 {@link CommandLine}.
	 */
	private CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
}
