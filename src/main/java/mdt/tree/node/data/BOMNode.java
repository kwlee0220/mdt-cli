package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.BOM;
import mdt.model.sm.data.DefaultBOM;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class BOMNode extends TerminalNode {
	public BOMNode(BOM bom) {
		setTitle("BOM");
		setValueType(String.format(" (%s)", bom.getBOMType()));
		
		String uomStr = ( bom.getItemUOMCode() != null ) ? String.format("%s", bom.getItemUOMCode()) : "";
		String value = String.format("tem=%s, quantity=%s%s", bom.getItemID(), bom.getBOMQuantity(), uomStr);
		setValue(value);
	}
	
	public static class BOMListNode extends SimpleListNode<BOM> {
		public BOMListNode(List<? extends BOM> elements) {
			super(elements, BOM -> FACTORY.create(BOM));
		}
	}

	public static BOMNodeFactory FACTORY = new BOMNodeFactory();
	public static class BOMNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultBOM BOM = new DefaultBOM();
			BOM.updateFromAasModel(sme);
			
			return create(BOM);
		}
		
		public DefaultNode create(BOM BOM) {
			return new BOMNode(BOM);
		}
	}
}