package mdt.cli.get.instance;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Funcs;
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
	
	private HttpMDTInstanceManager m_manager;
	private List<MDTParameterService> m_parameters;
	private MDTParameterDescriptorListNode m_paramNodes;

	public static final void main(String... args) throws Exception {
		main(new GetInstanceParametersCommand(), args);
	}
	
	public GetInstanceParametersCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw)
		throws SerializationException, IOException {
		List<MDTParameterDescriptor> descList = Funcs.map(m_parameters, MDTParameterService::getDescriptor);
		String json = MDTModelSerDe.JSON_SERIALIZER.writeList(descList);
		pw.println(json);
	}

	@Override
	protected Node toMDTModelNode(MDTInstance instance) throws IOException {
		m_manager = (HttpMDTInstanceManager)instance.getInstanceManager();
		
		 // parameter nodes
		if ( m_paramNodes == null ) {
			m_parameters = instance.getParameterServiceAll();
			m_paramNodes = new MDTParameterDescriptorListNode(instance.getId(), m_parameters);
		}
		
		return m_paramNodes;
	}
	
	private class MDTParameterDescriptorListNode extends ListNode {
		private List<ParameterNode> m_paramNodes;
		
		public MDTParameterDescriptorListNode(String instId, List<MDTParameterService> parameters) {
			setTitle("Parameters");
			setRepeatable(true);
			
			m_paramNodes = FStream.from(parameters)
									.map(ParameterNode::new)
									.toList();
		}
		
		@Override
		protected List<? extends DefaultNode> getElementNodes() {
			return m_paramNodes;
		}
	}
	
	private class ParameterNode extends DefaultNode {
		private final MDTParameterDescriptor m_paramDesc;
		private final MDTParameterReference m_paramRef;
		
		public ParameterNode(MDTParameterService param) {
			m_paramDesc = param.getDescriptor();
			m_paramRef = param.getReference();
			
			setTitle(m_paramDesc.getId());
			setValueType(String.format(" (%s)", m_paramDesc.getValueType()));
			setHideValue(true);
		}
		
		@Override
		public List<? extends Node> getChildren() {
			TerminalNode nameNode = new TerminalNode("name", "", m_paramDesc.getName());
			TerminalNode refNode = new TerminalNode("reference", "", m_paramDesc.getReference());
			TerminalNode epNode = new TerminalNode("endpoint", "", m_paramDesc.getEndpoint());
			try {
				if ( !m_paramRef.isActivated() ) {
					m_paramRef.activate(m_manager);
				}
				DefaultNode valueNode = DefaultNodeFactories.create(m_paramRef.read());
				valueNode.setTitle("value");
				return List.of(nameNode, refNode, epNode, valueNode);
			}
			catch ( Throwable e ) {
				return List.of(refNode);
			}
		}
	}
}
