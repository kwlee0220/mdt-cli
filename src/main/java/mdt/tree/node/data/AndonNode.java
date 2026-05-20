package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.Andon;
import mdt.model.sm.data.DefaultAndon;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class AndonNode extends TerminalNode {
	public AndonNode(Andon andon) {
		super("Andon", null, null);
		
		String value = String.format("Operation=%s, Start=%s, End=%s, Cause=%s",
										andon.getOperationID(),
										andon.getStartDateTime().trim(),
										andon.getEndDateTime().trim(),
										andon.getCauseName());
		setValue(value);
	}
	
	public static class AndonListNode extends SimpleListNode<Andon> {
		public AndonListNode(List<? extends Andon> elements) {
			super(elements, andon -> FACTORY.create(andon));
		}
	}

	public static AndonNodeFactory FACTORY = new AndonNodeFactory();
	public static class AndonNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultAndon andon = new DefaultAndon();
			andon.updateFromAasModel(sme);
			
			return create(andon);
		}
		
		public DefaultNode create(Andon andon) {
			return new AndonNode(andon);
		}
	}
}