package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.func.Optionals;

import mdt.model.sm.data.DefaultProductionOrder;
import mdt.model.sm.data.ProductionOrder;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ProductionOrderNode extends TerminalNode {
	public ProductionOrderNode(ProductionOrder plan) {
		setTitle(plan.getProductionOrderID());
		setValueType(String.format(" 공정(%s)", plan.getOperationID()));

		String uomStr = Optionals.getOrElse("" + plan.getItemUOMCode(), ""); 
		String value = String.format("%s(%s%s), schedule: %s~%s",
										plan.getItemID(), plan.getProductionOrderQuantity(), uomStr,
										plan.getScheduleStartDateTime(), plan.getScheduleEndDateTime());
		setValue(value);
	}
	
	public static class ProductionOrderListNode extends SimpleListNode<ProductionOrder> {
		public ProductionOrderListNode(List<? extends ProductionOrder> elements) {
			super(elements, ProductionOrder -> FACTORY.create(ProductionOrder));
		}
	}

	public static ProductionOrderNodeFactory FACTORY = new ProductionOrderNodeFactory();
	public static class ProductionOrderNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultProductionOrder ProductionOrder = new DefaultProductionOrder();
			ProductionOrder.updateFromAasModel(sme);
			
			return create(ProductionOrder);
		}
		
		public DefaultNode create(ProductionOrder ProductionOrder) {
			return new ProductionOrderNode(ProductionOrder);
		}
	}
}