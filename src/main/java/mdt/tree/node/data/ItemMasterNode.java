package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.DefaultItemMaster;
import mdt.model.sm.data.ItemMaster;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ItemMasterNode extends TerminalNode {
	public ItemMasterNode(ItemMaster itemMaster) {
		setTitle("Item");
		
		String value = String.format("%s (%s)", itemMaster.getItemName(), itemMaster.getItemType());
		setValue(value);
	}
	
	public static class ItemMasterListNode extends SimpleListNode<ItemMaster> {
		public ItemMasterListNode(List<? extends ItemMaster> elements) {
			super(elements, ItemMaster -> FACTORY.create(ItemMaster));
		}
	}

	public static ItemMasterNodeFactory FACTORY = new ItemMasterNodeFactory();
	public static class ItemMasterNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultItemMaster ItemMaster = new DefaultItemMaster();
			ItemMaster.updateFromAasModel(sme);
			
			return create(ItemMaster);
		}
		
		public DefaultNode create(ItemMaster ItemMaster) {
			return new ItemMasterNode(ItemMaster);
		}
	}
}