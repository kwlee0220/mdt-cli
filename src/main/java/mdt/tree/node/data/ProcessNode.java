package mdt.tree.node.data;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.func.FOption;

import mdt.model.sm.data.DefaultOperation;
import mdt.model.sm.data.Operation;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ProcessNode extends DefaultNode {
	private final Operation m_process;
	
	public ProcessNode(Operation equip) {
		m_process = equip;
		
		String nameStr = FOption.mapOrElse(equip.getOperationName(), n -> String.format(" (%s)", n), "");
		String title = String.format("%s%s", equip.getOperationId(), nameStr);
		setTitle(title);
		setValueType(" (Operation)");
	}
	
	@Override
	public Iterable<? extends Node> getChildren() {
		ParameterCollectionNode parameters = new ParameterCollectionNode(m_process);
		return List.of(parameters);
	}

	public static OperationNodeFactory FACTORY = new OperationNodeFactory();
	public static class OperationNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultOperation Operation = new DefaultOperation();
			Operation.updateFromAasModel(sme);
			
			return create(Operation);
		}
		
		public DefaultNode create(Operation Operation) {
			return new ProcessNode(Operation);
		}
	}
}
