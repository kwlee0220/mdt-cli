package mdt.tree.node;

import java.util.List;
import java.util.function.Function;

import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SimpleListNode<T> extends ListNode {
	private final List<? extends T> m_elements;
	private final Function<T,? extends DefaultNode> m_elmNodeFact;
	
	public SimpleListNode(List<? extends T> elements, Function<T,? extends DefaultNode> ellmNodeFact) {
		m_elements = elements;
		m_elmNodeFact = ellmNodeFact;
	}

	@Override
	protected List<? extends DefaultNode> getElementNodes() {
		return FStream.from(m_elements)
						.map(m_elmNodeFact::apply)
						.toList();
	}

}
