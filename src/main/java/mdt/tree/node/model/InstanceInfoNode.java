package mdt.tree.node.model;

import java.util.List;

import org.barfuin.texttree.api.Node;

import com.google.common.collect.Lists;

import mdt.model.instance.InstanceDescriptor;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.TerminalNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InstanceInfoNode extends DefaultNode {
	private List<Node> m_children = Lists.newArrayList();

	public InstanceInfoNode(InstanceDescriptor instDesc) {
		String title = String.format("%s (%s, %s)", instDesc.getId(), instDesc.getAssetType(), instDesc.getStatus());
		setTitle(title);

		m_children.add(new TerminalNode("aasId", "", instDesc.getAasId()));
		m_children.add(new TerminalNode("aasIdShort", "", instDesc.getAasIdShort()));
		m_children.add(new TerminalNode("assetId", "", instDesc.getGlobalAssetId()));
		m_children.add(new TerminalNode("baseEndpoint", "", instDesc.getBaseEndpoint()));
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		return m_children;
	}
}