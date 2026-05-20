package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.DefaultRepair;
import mdt.model.sm.data.Repair;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class RepairNode extends TerminalNode {
	public RepairNode(Repair repair) {
		setTitle("Repair");
		setValueType(" 진단");
		
		String value = String.format("%s:%s: %s, 발생공정: %s, item=%s",
										repair.getDefectRegOperationID(), repair.getDefectRegEquipmentID(),
										repair.getDefectRegDateTime(), repair.getDetectedProcess(),
										repair.getProductionItemSerialNO());
		setValue(value);
	}
	
	public static class RepairListNode extends SimpleListNode<Repair> {
		public RepairListNode(List<? extends Repair> elements) {
			super(elements, Repair -> FACTORY.create(Repair));
		}
	}

	public static RepairNodeFactory FACTORY = new RepairNodeFactory();
	public static class RepairNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultRepair Repair = new DefaultRepair();
			Repair.updateFromAasModel(sme);
			
			return create(Repair);
		}
		
		public DefaultNode create(Repair Repair) {
			return new RepairNode(Repair);
		}
	}
}