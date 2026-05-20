package mdt.tree.node.op;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;

import utils.func.FOption;
import utils.stream.FStream;

import mdt.model.DefaultInput;
import mdt.model.Input;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.ListNode;
import mdt.tree.node.SimpleListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class InputArgumentNode {
	private InputArgumentNode() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	public static InputArgumentNodeFactory FACTORY = new InputArgumentNodeFactory();
	public static class InputArgumentNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultInput input = new DefaultInput();
			input.updateFromAasModel(sme);
			
			return create(input);
		}
		
		public DefaultNode create(Input input) {
			DefaultNode node = DefaultNodeFactories.create(input.getInputValue());
			
			String nameStr = FOption.mapOrElse(input.getInputName(), str -> String.format("(%s)", str), "");
			node.setTitle(String.format("%s%s", input.getInputID(), nameStr));
			return node;
		}
	}
	
	public static class InputArgumentListNode extends SimpleListNode<Input> {
		public InputArgumentListNode(List<? extends Input> elements) {
			super(elements, input -> FACTORY.create(input));
		}
	}
	public static class InputArgumentSmeListNode extends ListNode {
		private final List<DefaultNode> m_elementNodes;
		
		public InputArgumentSmeListNode(SubmodelElementList sml) {
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