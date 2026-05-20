package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.DefaultProductionPlanning;
import mdt.model.sm.data.ProductionPlanning;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ProductionPlanningNode extends TerminalNode {
	public ProductionPlanningNode(ProductionPlanning plan) {
		setTitle("ProductionPlanning");
		
		String value = String.format("물품: %s, 수량: %s, 계획: %s ~ %s",
									plan.getItemID(), plan.getProductionPlanQuantity(),
									plan.getScheduleStartDateTime(), plan.getScheduleEndDateTime());
		setValue(value);
	}
	
	public static class ProductionPlanningListNode extends SimpleListNode<ProductionPlanning> {
		public ProductionPlanningListNode(List<? extends ProductionPlanning> elements) {
			super(elements, ProductionPlanning -> FACTORY.create(ProductionPlanning));
		}
	}

	public static ProductionPlanningNodeFactory FACTORY = new ProductionPlanningNodeFactory();
	public static class ProductionPlanningNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultProductionPlanning ProductionPlanning = new DefaultProductionPlanning();
			ProductionPlanning.updateFromAasModel(sme);
			
			return create(ProductionPlanning);
		}
		
		public DefaultNode create(ProductionPlanning ProductionPlanning) {
			return new ProductionPlanningNode(ProductionPlanning);
		}
	}
}