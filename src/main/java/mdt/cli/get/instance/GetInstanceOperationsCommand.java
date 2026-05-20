package mdt.cli.get.instance;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.client.instance.HttpMDTInstanceClient;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTOperationDescriptor;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.ListNode;
import mdt.tree.node.TerminalNode;

import picocli.CommandLine.Command;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "operations",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get MDT operation (AI/Simulation) informations for the MDTInstance."
)
public class GetInstanceOperationsCommand extends AbstractInstanceSubCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetInstanceOperationsCommand.class);

	public static final void main(String... args) throws Exception {
		main(new GetInstanceOperationsCommand(), args);
	}
	
	public GetInstanceOperationsCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw) throws SerializationException, IOException {
		List<MDTOperationDescriptor> descList = instance.getMDTOperationDescriptorAll();
		String json = MDTModelSerDe.JSON_SERIALIZER.writeList(descList);
		pw.println(json);
	}

	@Override
	protected Node toMDTModelNode(MDTInstance instance) throws IOException {
		return new OperationInfoListNode(instance.getMDTOperationDescriptorAll());
	}

	private static class OperationInfoListNode extends DefaultNode {
		protected List<OperationInfoNode> m_opNodeList;
		
		public OperationInfoListNode(List<MDTOperationDescriptor> opList) {
			setTitle("Operations");
			m_opNodeList = FStream.from(opList)
									.map(OperationInfoNode::new)
									.toList();
		}
	
		@Override
		public Iterable<? extends Node> getChildren() {
			return m_opNodeList;
		}
		
		static class OperationInfoNode extends DefaultNode {
			private final List<ArgumentListNode> m_children;
			
			public OperationInfoNode(MDTOperationDescriptor op) {
				setTitle(op.getId());
				setValueType(String.format(" (%s)", op.getOperationType()));
	
				ArgumentListNode inputListNode = new ArgumentListNode("Inputs", op.getInputArguments());
				ArgumentListNode outputListNode = new ArgumentListNode("Outputs", op.getOutputArguments());
				m_children = List.of(inputListNode, outputListNode);
			}
	
			@Override
			public Iterable<? extends Node> getChildren() {
				return m_children;
			}
		}
		
		static class ArgumentListNode extends ListNode {
			private final List<TerminalNode> m_argNodes;
			
			public ArgumentListNode(String title, List<MDTOperationDescriptor.ArgumentDescriptor> args) {
				setTitle(title);
				
				m_argNodes = FStream.from(args)
									.map(this::create)
									.toList();
			}
	
			@Override
			protected List<? extends DefaultNode> getElementNodes() {
				return m_argNodes;
			}
			
			private TerminalNode create(MDTOperationDescriptor.ArgumentDescriptor arg) {
				TerminalNode node = new TerminalNode();
				node.setTitle(arg.getId());
				node.setValueType(String.format(" (%s)", arg.getValueType()));
				node.setHideValue(true);
				
				return node;
			}
		}
	}
}
