package mdt.tree.node;

import java.util.List;

import org.barfuin.texttree.api.Node;

import com.google.common.collect.Lists;

import mdt.model.sm.simulation.Simulation;
import mdt.model.sm.simulation.SimulationInfo;
import mdt.model.sm.simulation.SimulationTool;
import mdt.tree.node.op.InputArgumentNode.InputArgumentListNode;
import mdt.tree.node.op.OutputArgumentNode.OutputArgumentListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class SimulationSubmodelNode extends DefaultNode {
	private Simulation m_simulation;
	
	public SimulationSubmodelNode(Simulation simulation) {
		m_simulation = simulation;
	}

	@Override
	public String getText() {
		return "Simulation";
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		List<Node> children = Lists.newArrayList();

		SimulationInfo simInfo = m_simulation.getSimulationInfo();
		
		SimulationTool tool = simInfo.getSimulationTool();
		children.add(new TerminalNode("SimulationTool", null, tool.getSimToolName()));
//		if ( tool.getSimulatorEndpoint() != null ) {
//			children.add(new TextNode(String.format("SimulatorEndpoint: %s", tool.getSimulatorEndpoint())));
//		}
//		children.add(new TextNode(String.format("SimulationTimeout: %s",
//											DataTypes.DURATION.toValueString(tool.getSimulationTimeout()))));
//		children.add(new TextNode(String.format("SessionRetainTimeout: %s",
//											DataTypes.DURATION.toValueString(tool.getSessionRetainTimeout()))));
		
		children.add(new InputArgumentListNode(simInfo.getInputs()));
		children.add(new OutputArgumentListNode(simInfo.getOutputs()));
		
		children.add(new AASOperationNode(m_simulation.getSimulationOperation()));
		
		return children;
	}
}