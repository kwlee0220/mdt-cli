package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.DefaultProductionPerformance;
import mdt.model.sm.data.ProductionPerformance;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ProductionPerformanceNode extends TerminalNode {
	public ProductionPerformanceNode(ProductionPerformance plan) {
		setTitle("ProductionPerformance");
		String value = String.format("operation=%s, item=%s, quantity=%s",
										plan.getOperationID(), plan.getItemID(), plan.getProducedQuantity());
		setValue(value);
	}
	
	public static class ProductionPerformanceListNode extends SimpleListNode<ProductionPerformance> {
		public ProductionPerformanceListNode(List<? extends ProductionPerformance> elements) {
			super(elements, ProductionPerformance -> FACTORY.create(ProductionPerformance));
		}
	}

	public static ProductionPerformanceNodeFactory FACTORY = new ProductionPerformanceNodeFactory();
	public static class ProductionPerformanceNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultProductionPerformance ProductionPerformance = new DefaultProductionPerformance();
			ProductionPerformance.updateFromAasModel(sme);
			
			return create(ProductionPerformance);
		}
		
		public DefaultNode create(ProductionPerformance ProductionPerformance) {
			return new ProductionPerformanceNode(ProductionPerformance);
		}
	}
}