package mdt.cli.get.instance;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Funcs;
import utils.func.Try;
import utils.stream.FStream;

import mdt.client.instance.HttpMDTInstanceClient;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTParameterDescriptor;
import mdt.model.instance.MDTParameterService;
import mdt.model.sm.ref.MDTParameterReference;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;
import mdt.tree.node.ListNode;
import mdt.tree.node.TerminalNode;

import picocli.CommandLine.Command;

/**
 * MDTInstance의 파라미터 목록과 현재 값을 출력하는 {@code get instance <instance-id> parameters} 명령.
 * <p>
 * 트리 출력에서는 파라미터 값을 먼저 모두 읽은 뒤 그 결과로 트리를 만든다. 값을 읽지 못한 파라미터는
 * 해당 노드에 {@code error} 항목으로 원인을 보인다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "parameters",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get MDT parameter informations for the MDTInstance."
)
public class GetInstanceParametersCommand extends AbstractInstanceSubCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetInstanceParametersCommand.class);

	public static final void main(String... args) throws Exception {
		main(new GetInstanceParametersCommand(), args);
	}
	
	public GetInstanceParametersCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw)
		throws SerializationException, IOException {
		List<MDTParameterDescriptor> descList = Funcs.map(instance.getParameterServiceAll(),
														MDTParameterService::getDescriptor);
		String json = MDTModelSerDe.JSON_SERIALIZER.writeList(descList);
		pw.println(json);
	}

	@Override
	protected Node toMDTModelNode(MDTInstance instance) throws IOException {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)instance.getInstanceManager();
		List<ParameterNode> paramNodes = FStream.from(instance.getParameterServiceAll())
												.map(param -> new ParameterNode(param.getDescriptor(),
																				readValue(param, manager)))
												.toList();
		return new ParameterListNode(paramNodes);
	}
	
	/**
	 * 파라미터의 현재 값을 읽는다.
	 *
	 * @param param		대상 파라미터.
	 * @param manager	참조 활성화에 쓸 인스턴스 매니저.
	 * @return	읽은 값 또는 실패 원인.
	 */
	private Try<SubmodelElement> readValue(MDTParameterService param, HttpMDTInstanceManager manager) {
		MDTParameterReference ref = param.getReference();
		return Try.of(() -> {
			if ( !ref.isActivated() ) {
				ref.activate(manager);
			}
			return ref.read();
		});
	}
	
	private static class ParameterListNode extends ListNode {
		private final List<ParameterNode> m_paramNodes;
		
		ParameterListNode(List<ParameterNode> paramNodes) {
			setTitle("Parameters");
			setRepeatable(true);
			m_paramNodes = paramNodes;
		}
		
		@Override
		protected List<? extends DefaultNode> getElementNodes() {
			return m_paramNodes;
		}
	}
	
	/** 이미 읽은 파라미터 값(또는 실패 원인)을 보이는 노드. 원격 조회를 하지 않는다. */
	private static class ParameterNode extends DefaultNode {
		private final MDTParameterDescriptor m_paramDesc;
		private final Try<SubmodelElement> m_value;
		
		ParameterNode(MDTParameterDescriptor paramDesc, Try<SubmodelElement> value) {
			m_paramDesc = paramDesc;
			m_value = value;
			
			setTitle(m_paramDesc.getId());
			setValueType(String.format(" (%s)", m_paramDesc.getValueType()));
			setHideValue(true);
		}
		
		@Override
		public List<? extends Node> getChildren() {
			TerminalNode nameNode = new TerminalNode("name", "", m_paramDesc.getName());
			TerminalNode refNode = new TerminalNode("reference", "", m_paramDesc.getReference());
			TerminalNode epNode = new TerminalNode("endpoint", "", m_paramDesc.getEndpoint());
			if ( m_value.isSuccessful() ) {
				DefaultNode valueNode = DefaultNodeFactories.create(m_value.get());
				valueNode.setTitle("value");
				return List.of(nameNode, refNode, epNode, valueNode);
			}
			else {
				Throwable cause = m_value.getCause();
				TerminalNode errorNode = new TerminalNode("error", "", "" + cause);
				return List.of(nameNode, refNode, epNode, errorNode);
			}
		}
	}
}
