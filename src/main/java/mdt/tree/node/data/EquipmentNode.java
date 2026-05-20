package mdt.tree.node.data;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.func.FOption;

import mdt.model.sm.data.DefaultEquipment;
import mdt.model.sm.data.Equipment;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class EquipmentNode extends DefaultNode {
	private final Equipment m_equip;
	
	public EquipmentNode(Equipment equip) {
		m_equip = equip;
		
		String nameStr = FOption.mapOrElse(equip.getEquipmentName(), n -> String.format(" (%s)", n), "");
		String title = String.format("%s%s", equip.getEquipmentId(), nameStr);
		setTitle(title);
		setValueType(" (Equipment)");
		setValue("");
	}
	
	@Override
	public Iterable<? extends Node> getChildren() {
		ParameterCollectionNode parameters = new ParameterCollectionNode(m_equip);
		return List.of(parameters);
	}

	public static EquipmentNodeFactory FACTORY = new EquipmentNodeFactory();
	public static class EquipmentNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultEquipment Equipment = new DefaultEquipment();
			Equipment.updateFromAasModel(sme);
			
			return create(Equipment);
		}
		
		public DefaultNode create(Equipment Equipment) {
			return new EquipmentNode(Equipment);
		}
	}
}
