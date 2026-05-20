package mdt.tree.node.info;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.info.CompositionItem;
import mdt.model.sm.info.DefaultCompositionItem;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.TerminalNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CompositionItemNode extends TerminalNode {
	public CompositionItemNode(CompositionItem item) {
		setTitle(item.getID());
		setValue(item.getReference());
	}

	public static CompositionItemNodeFactory FACTORY = new CompositionItemNodeFactory();
	public static class CompositionItemNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultCompositionItem CompositionItem = new DefaultCompositionItem();
			CompositionItem.updateFromAasModel(sme);
			
			return create(CompositionItem);
		}
		
		public DefaultNode create(CompositionItem dep) {
			return new CompositionItemNode(dep);
		}
	}
}
