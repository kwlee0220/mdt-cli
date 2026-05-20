package mdt.tree.node.data;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import com.google.common.collect.Lists;

import mdt.model.sm.data.DefaultLine;
import mdt.model.sm.data.Line;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class LineNode extends DefaultNode {
	private Line m_line;
	
	public LineNode(Line line) {
		m_line = line;
		
		setTitle(m_line.getLineID());
		setValueType(" (Line)");
		
		String value = m_line.getLineStatus();
		setValue(value);
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		List<Node> children = Lists.newArrayList();
		
//		if ( m_line.getBOMs().size() > 0 ) {
//			ListNode<BOM> boms = new ListNode<>("BOMs", m_line.getBOMs(), BOMNode::new);
//			children.add(boms);
//		}
//		if ( m_line.getItemMasters().size() > 0 ) {
//			ListNode<ItemMaster> repairs = new ListNode<>("ItemMasters", m_line.getItemMasters(), ItemMasterNode::new);
//			children.add(repairs);
//		}
//		if ( m_line.getRoutings().size() > 0 ) {
//			ListNode<Routing> repairs = new ListNode<>("Routings", m_line.getRoutings(), RoutingNode::new);
//			children.add(repairs);
//		}
		
		return children;
	}
	
	public static class LineListNode extends SimpleListNode<Line> {
		public LineListNode(List<Line> elements) {
			super(elements, Line -> FACTORY.create(Line));
		}
	}

	public static LineNodeFactory FACTORY = new LineNodeFactory();
	public static class LineNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultLine Line = new DefaultLine();
			Line.updateFromAasModel(sme);
			
			return create(Line);
		}
		
		public DefaultNode create(Line Line) {
			return new LineNode(Line);
		}
	}
}
