package mdt.cli.get;

import java.io.PrintWriter;
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

import utils.stream.FStream;
import utils.func.Nullables;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.RepeatOptions;
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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
	@Parameters(index="0", paramLabel="instance-id", description="MDTInstance id to show.")
	private String m_instanceId;

	@Option(names={"--info", "-i"}, description="show MDTInfo")
	private boolean m_info = false;

	@Option(names={"--parameters", "-p"}, description="show parameters")
	private boolean m_parameters = false;

	@Option(names={"--operations", "-o"}, description="show operations (AI or Simulation)")
	private boolean m_operations = false;

	@Option(names={"--compositions", "-c"}, description="show compositions")
	private boolean m_compositions = false;

	@Mixin private RepeatOptions m_repeatOpts;

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
	 * {@code --repeat} 옵션이 지정되지 않은 경우에는 한 번만 정보를 출력하고 종료한다. 이때 정보를 가져오지 못하면
	 * 예외가 그대로 전달된다({@code error:} 메시지와 종료 코드 1).
	 * 지정된 경우에는 {@link RepeatOptions}을 이용해서 주어진 간격으로 화면을 갱신하며
	 * 출력을 반복한다. 반복 출력 중 인스턴스가 실행 중이 아니거나 정보를 가져오는데 실패한 경우에는
	 * 오류 메시지를 출력하고 다음 갱신을 기다린다.
	 *
	 * @param mdt {@link MDTManager} 인스턴스
	 * @throws Exception 명령 실행 도중 발생한 예외
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		m_repeatOpts.run(pw -> printOutput(manager.getInstance(m_instanceId), pw),
						"get MDT info of " + m_instanceId, m_verbose);
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
			Nullables.accept(buildInfoNode(instance, pw), nodes::add);
		}
		if ( showParameters ) {
			try {
				nodes.add(buildParametersNode(instance));
			}
			catch ( ResourceNotFoundException e ) {
				throw usageError("No Data Submodel found in MDTInstance.");
			}
		}
		if ( showOperations ) {
			Nullables.accept(buildOperationsNode(instance), nodes::add);
		}
		if ( showCompositions ) {
			SubmodelService infoSvc = getInformationModelService(instance);
			if ( infoSvc == null ) {
				if ( m_verbose ) {
					pw.println("No InformationModel found.");
				}
			}
			else {
				Nullables.accept(buildCompositionSubNode(infoSvc, "TwinComposition.CompositionItems",
														"Components", "CompositionItems", pw),
								nodes::add);
				Nullables.accept(buildCompositionSubNode(infoSvc, "TwinComposition.CompositionDependencies",
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

}
