package mdt.tree.node.data;

import java.util.List;

import org.barfuin.texttree.api.Node;

import utils.InternalException;

import mdt.model.sm.data.Andon;
import mdt.model.sm.data.Andons;
import mdt.model.sm.data.BOM;
import mdt.model.sm.data.BOMs;
import mdt.model.sm.data.Data;
import mdt.model.sm.data.Equipment;
import mdt.model.sm.data.ItemMaster;
import mdt.model.sm.data.ItemMasters;
import mdt.model.sm.data.Line;
import mdt.model.sm.data.Operation;
import mdt.model.sm.data.ProductionOrder;
import mdt.model.sm.data.ProductionOrders;
import mdt.model.sm.data.ProductionPerformance;
import mdt.model.sm.data.ProductionPerformances;
import mdt.model.sm.data.ProductionPlanning;
import mdt.model.sm.data.ProductionPlannings;
import mdt.model.sm.data.Repair;
import mdt.model.sm.data.Repairs;
import mdt.model.sm.data.Routing;
import mdt.model.sm.data.Routings;
import mdt.model.sm.entity.SubmodelElementEntity;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.data.AndonNode.AndonListNode;
import mdt.tree.node.data.BOMNode.BOMListNode;
import mdt.tree.node.data.ItemMasterNode.ItemMasterListNode;
import mdt.tree.node.data.ProductionOrderNode.ProductionOrderListNode;
import mdt.tree.node.data.ProductionPerformanceNode.ProductionPerformanceListNode;
import mdt.tree.node.data.ProductionPlanningNode.ProductionPlanningListNode;
import mdt.tree.node.data.RepairNode.RepairListNode;
import mdt.tree.node.data.RoutingNode.RoutingListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class DataSubmodelNode extends DefaultNode {
	private Data m_data;
	
	public DataSubmodelNode(Data data) {
		m_data = data;
	}

	@Override
	public String getText() {
		return String.format("Data (%s)", m_data.getSubModelInfo().getTitle());
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		return m_data.getDataInfo()
						.getSubmodelElementEntityAll()
						.stream()
						.filter(ent -> !(ent instanceof ProductionOrders))
						.map(this::toNode)
						.toList();
	}

	private Node toNode(SubmodelElementEntity entity) {
		Class<?> entityClass = entity.getClass();
		if ( Line.class.isAssignableFrom(entityClass) ) {
			return new LineNode((Line)entity);
		}
		else if ( Equipment.class.isAssignableFrom(entityClass) ) {
			return new EquipmentNode((Equipment)entity);
		}
		else if ( Operation.class.isAssignableFrom(entityClass) ) {
			return new ProcessNode((Operation)entity);
		}
		else if ( ProductionPlannings.class.isAssignableFrom(entityClass) ) {
			List<? extends ProductionPlanning> plannings = ((ProductionPlannings)entity).getElementAll();
			return new ProductionPlanningListNode(plannings);
		}
		else if ( ProductionOrders.class.isAssignableFrom(entityClass) ) {
			List<? extends ProductionOrder> orders = ((ProductionOrders)entity).getElementAll();
			return new ProductionOrderListNode(orders);
		}
		else if ( ProductionPerformances.class.isAssignableFrom(entityClass) ) {
			List<? extends ProductionPerformance> perfs = ((ProductionPerformances)entity).getElementAll();
			return new ProductionPerformanceListNode(perfs);
		}
		else if ( Repairs.class.isAssignableFrom(entityClass) ) {
			List<? extends Repair> repairs = ((Repairs)entity).getElementAll();
			return new RepairListNode(repairs);
		}
		else if ( ItemMasters.class.isAssignableFrom(entityClass) ) {
			List<? extends ItemMaster> items = ((ItemMasters)entity).getElementAll();
			return new ItemMasterListNode(items);
		}
		else if ( Andons.class.isAssignableFrom(entityClass) ) {
			List<? extends Andon> andons = ((Andons)entity).getElementAll();
			return new AndonListNode(andons);
		}
		else if ( BOMs.class.isAssignableFrom(entityClass) ) {
			List<? extends BOM> boms = ((BOMs)entity).getElementAll();
			return new BOMListNode(boms);
		}
		else if ( Routings.class.isAssignableFrom(entityClass) ) {
			List<? extends Routing> routings = ((Routings)entity).getElementAll();
			return new RoutingListNode(routings);
		}
		else {
			throw new InternalException("Unknown KSX9101RootEntity: id=" + entityClass.getName());
		}
	}
}