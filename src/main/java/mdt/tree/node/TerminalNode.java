package mdt.tree.node;

import java.util.Collections;

import org.barfuin.texttree.api.Node;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TerminalNode extends DefaultNode {
	public TerminalNode() { }
	public TerminalNode(Object title, Object valueType, Object value) {
		super(title, valueType, value);
	}
	
	@Override
	public final Iterable<? extends Node> getChildren() {
		return Collections.emptyList();
	}
	
	public static TerminalNode text(String text) {
		TerminalNode node = new TerminalNode();
		node.setText(text);
		return node;
	}
}
