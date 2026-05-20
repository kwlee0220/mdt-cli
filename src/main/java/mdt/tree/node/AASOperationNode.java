package mdt.tree.node;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;

import com.google.common.collect.Lists;

import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class AASOperationNode extends DefaultNode {
	private final Operation m_operation;
	
	public AASOperationNode(Operation operation) {
		m_operation = operation;
		
		setTitle("Operation Variables");
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		List<Node> children = Lists.newArrayList();
		
		children.add(new VariableListNode("InputVariables", m_operation.getInputVariables()));
		children.add(new VariableListNode("InoutputVariables", m_operation.getInoutputVariables()));
		children.add(new VariableListNode("OutputVariables", m_operation.getOutputVariables()));
		
		return children;
	}

	private static class VariableListNode extends ListNode {
		private final List<OperationVariable> m_vars;
		
		private VariableListNode(String title, List<OperationVariable> vars) {
			m_vars = vars;
			
			setTitle(title);
		}

		@Override
		protected List<? extends DefaultNode> getElementNodes() {
			return FStream.from(m_vars)
							.map(var -> DefaultNodeFactories.create(var.getValue()))
							.toList();
		}
	}
}