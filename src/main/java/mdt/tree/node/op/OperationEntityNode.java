package mdt.tree.node.op;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;

import mdt.model.sm.SubmodelUtils;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.ListNode;
import mdt.tree.node.op.InputArgumentNode.InputArgumentSmeListNode;
import mdt.tree.node.op.OutputArgumentNode.OutputArgumentSmeListNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OperationEntityNode extends DefaultNode {
	private ListNode m_inputsNode;
	private ListNode m_outputsNode;
	
	public OperationEntityNode(String opId, SubmodelElement sme) {
		super(opId, null, "");
		
		SubmodelElementList inputsSml = SubmodelUtils.getFieldById((SubmodelElementCollection)sme,
																	"Inputs", SubmodelElementList.class);
		m_inputsNode = new InputArgumentSmeListNode(inputsSml);
		
		SubmodelElementList outputSml = SubmodelUtils.getFieldById((SubmodelElementCollection)sme,
																	"Outputs", SubmodelElementList.class);
		m_outputsNode = new OutputArgumentSmeListNode(outputSml);
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		return List.of(m_inputsNode, m_outputsNode);
	}
}
