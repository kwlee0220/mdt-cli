package mdt.tree.node.op;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;

import utils.func.FOption;
import utils.stream.FStream;

import mdt.model.DefaultOutput;
import mdt.model.Output;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.ListNode;
import mdt.tree.node.SimpleListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class OutputArgumentNode {
	private OutputArgumentNode() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	public static OutputArgumentNodeFactory FACTORY = new OutputArgumentNodeFactory();
	public static class OutputArgumentNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultOutput output = new DefaultOutput();
			output.updateFromAasModel(sme);
			
			return create(output);
		}
		
		public DefaultNode create(Output output) {
			DefaultNode node = DefaultNodeFactories.create(output.getOutputValue());
			
			String nameStr = FOption.mapOrElse(output.getOutputName(), str -> String.format("(%s)", str), "");
			node.setTitle(String.format("%s%s", output.getOutputID(), nameStr));
			return node;
		}
	}
	
	public static class OutputArgumentListNode extends SimpleListNode<Output> {
		public OutputArgumentListNode(List<? extends Output> elements) {
			super(elements, Output -> FACTORY.create(Output));
		}
	}
	public static class OutputArgumentSmeListNode extends ListNode {
		private final List<DefaultNode> m_elementNodes;
		
		public OutputArgumentSmeListNode(SubmodelElementList sml) {
			super(sml.getIdShort());
			
			m_elementNodes = FStream.from(sml.getValue())
									.map(FACTORY::create)
									.toList();
		}

		@Override
		protected List<? extends DefaultNode> getElementNodes() {
			return m_elementNodes;
		}
		
	}
}