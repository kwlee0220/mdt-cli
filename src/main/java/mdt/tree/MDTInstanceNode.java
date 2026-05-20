package mdt.tree;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.stream.FStream;

import mdt.model.sm.data.Data;
import mdt.model.sm.data.DefaultData;
import mdt.model.sm.info.DefaultInformationModel;
import mdt.model.sm.info.InformationModel;
import mdt.model.sm.info.MDTInfo;
import mdt.model.sm.simulation.DefaultSimulation;
import mdt.model.sm.simulation.Simulation;
import mdt.tree.node.InformationModelSubmodelNode;
import mdt.tree.node.SimulationSubmodelNode;
import mdt.tree.node.data.DataSubmodelNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTInstanceNode implements Node {
	private final String m_mdtId;
	private final InformationModel m_info;
	private final Data m_data;
	private final Simulation m_simulation;
	
	private MDTInstanceNode(Builder builder) {
		m_mdtId = builder.m_mdtId;
		m_info = builder.m_info;
		m_data = builder.m_data;
		m_simulation = builder.m_simulation;
	}

	@Override
	public String getText() {
		MDTInfo mdtInfo = m_info.getMDTInfo();
		String id = ObjectUtils.defaultIfNull(mdtInfo.getAssetName(), mdtInfo.getIdShort());
		String typeStr = mdtInfo.getAssetType() != null
						? String.format(" (%s)", mdtInfo.getAssetType()) : "";
		ObjectUtils.defaultIfNull(mdtInfo.getAssetType(), "");
		return String.format("%s: %s%s, status=%s", m_mdtId, id, typeStr, mdtInfo.getStatus());
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		List<Node> children = Lists.newArrayList();
		
		if ( m_info != null ) {
			InformationModelSubmodelNode infoNode = new InformationModelSubmodelNode(m_info);
			children.add(infoNode);
		}
		
		if ( m_data != null ) {
			children.add(new DataSubmodelNode(m_data));
		}

		if ( m_simulation != null ) {
			children.add(new SimulationSubmodelNode(m_simulation));
		}
		
		return children;
	}

//	private Node toNode(TopLevelEntity entity) {
//		Class<?> entityClass = entity.getClass();
//		if ( Line.class.isAssignableFrom(entityClass) ) {
//			return new LineNode((Line)entity);
//		}
//		else if ( Equipment.class.isAssignableFrom(entityClass) ) {
//			return new EquipmentNode((Equipment)entity);
//		}
//		else if ( Operation.class.isAssignableFrom(entityClass) ) {
//			return new OperationNode((Operation)entity);
//		}
//		else if ( ProductionPlannings.class.isAssignableFrom(entityClass) ) {
//			List<? extends ProductionPlanning> plannings = ((ProductionPlannings)entity).getElements();
//			if ( plannings.size() != 1 ) {
//				return new ListNode<>("ProductionPlannings", plannings, ProductionPlanningNode::new);
//			}
//			else {
//				return new ProductionPlanningNode(plannings.get(0));
//			}
//		}
//		else if ( ProductionOrders.class.isAssignableFrom(entityClass) ) {
//			List<? extends ProductionOrder> orders = ((ProductionOrders)entity).getElements();
//			if ( orders.size() != 1 ) {
//				return new ListNode<>("ProductionOrders", orders, ProductionOrderNode::new);
//			}
//			else {
//				return new ProductionOrderNode(orders.get(0));
//			}
//		}
//		else if ( ProductionPerformances.class.isAssignableFrom(entityClass) ) {
//			List<? extends ProductionPerformance> perfs = ((ProductionPerformances)entity).getElements();
//			if ( perfs.size() != 1 ) {
//				return new ListNode<>("ProductionPerformances", perfs, ProductionPerformanceNode::new);
//			}
//			else {
//				return new ProductionPerformanceNode(perfs.get(0));
//			}
//		}
//		else if ( Repairs.class.isAssignableFrom(entityClass) ) {
//			List<? extends Repair> repairs = ((Repairs)entity).getElements();
//			if ( repairs.size() != 1 ) {
//				return new ListNode<>("Repairs", repairs, RepairNode::new);
//			}
//			else {
//				return new RepairNode(repairs.get(0));
//			}
//		}
//		else if ( ItemMasters.class.isAssignableFrom(entityClass) ) {
//			List<? extends ItemMaster> items = ((ItemMasters)entity).getElements();
//			if ( items.size() != 1 ) {
//				return new ListNode<>("ItemMasters", items, ItemMasterNode::new);
//			}
//			else {
//				return new ItemMasterNode(items.get(0));
//			}
//		}
//		else if ( Andons.class.isAssignableFrom(entityClass) ) {
//			List<? extends Andon> andons = ((Andons)entity).getElements();
//			if ( andons.size() != 1 ) {
//				return new ListNode<>("Andons", andons, AndonNode::new);
//			}
//			else {
//				return new AndonNode(andons.get(0));
//			}
//		}
//		else if ( BOMs.class.isAssignableFrom(entityClass) ) {
//			List<? extends BOM> boms = ((BOMs)entity).getElements();
//			if ( boms.size() != 1 ) {
//				return new ListNode<>("BOMs", boms, BOMNode::new);
//			}
//			else {
//				return new BOMNode(boms.get(0));
//			}
//		}
//		else if ( Routings.class.isAssignableFrom(entityClass) ) {
//			List<? extends Routing> routings = ((Routings)entity).getElements();
//			if ( routings.size() != 1 ) {
//				return new ListNode<>("Routings", routings, RoutingNode::new);
//			}
//			else {
//				return new RoutingNode(routings.get(0));
//			}
//		}
//		else if ( Equipments.class.isAssignableFrom(entityClass) ) {
//			List<? extends Equipment> equipments = ((Equipments)entity).getElements();
//			if ( equipments.size() != 1 ) {
//				return new ListNode<>("Equipments", equipments, EquipmentNode::new);
//			}
//			else {
//				return new EquipmentNode(equipments.get(0));
//			}
//		}
//		else {
//			throw new InternalException("Unknown KSX9101RootEntity: id=" + entityClass.getName());
//		}
//	}
	
	public static MDTInstanceNode fromSubmodelList(String mdtId, List<Submodel> submodelList) {
		Map<String,Submodel> submodels = FStream.from(submodelList)
												.tagKey(Submodel::getIdShort)
												.toMap();
		
		Builder builder = builder().mdtId(mdtId);
		
		Submodel submodel;
		
		submodel = submodels.get("InformationModel");
		if ( submodel != null ) {
			DefaultInformationModel adaptor = DefaultInformationModel.from(submodel);
			builder = builder.informationModelSubmodel(adaptor);
		}
		
		submodel = submodels.get("Data");
		if ( submodel != null ) {
			DefaultData adaptor = DefaultData.from(submodel);
			builder = builder.dataSubmodel(adaptor);
		}
		
		submodel = submodels.get("Simulation");
		if ( submodel != null ) {
			DefaultSimulation adaptor = DefaultSimulation.from(submodel);
			builder = builder.simulationSubmodel(adaptor);
		}
		
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
	public static final class Builder {
		private String m_mdtId;
		private InformationModel m_info;
		private Data m_data;
		private Simulation m_simulation;
		
		public MDTInstanceNode build() {
			Preconditions.checkArgument(m_mdtId != null);
			Preconditions.checkArgument(m_info != null);
//			Preconditions.checkArgument(m_data != null);
			
			return new MDTInstanceNode(this);
		}
		
		public Builder mdtId(String mdtId) {
			m_mdtId = mdtId;
			return this;
		}
		
		public Builder informationModelSubmodel(InformationModel info) {
			m_info = info;
			return this;
		}
		
		public Builder dataSubmodel(Data data) {
			m_data = data;
			return this;
		}
		
		public Builder simulationSubmodel(Simulation simulation) {
			m_simulation = simulation;
			return this;
		}
	}
}
