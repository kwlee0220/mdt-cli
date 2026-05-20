package mdt.tree.node;

import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.BasicEventElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import com.google.common.collect.Lists;

import utils.stream.FStream;

import mdt.client.support.Utils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class BasicEventElementNode extends DefaultNode {
	private BasicEventElement m_event;
	
	public BasicEventElementNode(BasicEventElement event) {
		m_event = event;
		setTitle(event.getIdShort());
	}

	@Override
	public Iterable<? extends Node> getChildren() {
		List<Node> attributes = Lists.newArrayList();
		attributes.add(TerminalNode.text("Observed: " + m_event.getObserved().getKeys().get(0).getValue()));
		attributes.add(TerminalNode.text("Direction: " + m_event.getDirection()));
		attributes.add(TerminalNode.text("State: " + m_event.getState()));
		attributes.add(TerminalNode.text("MessageTopic: " + m_event.getMessageTopic()));
		attributes.add(TerminalNode.text("MessageBroker: " + m_event.getMessageBroker()));
		attributes.add(TerminalNode.text("LastUpdate: " + m_event.getLastUpdate()));
		attributes.add(TerminalNode.text("MinInterval: " + m_event.getMinInterval()));
		attributes.add(TerminalNode.text("MaxInterval: " + m_event.getMaxInterval()));
		attributes.add(TerminalNode.text("DisplayName: " + Utils.concatLangStringNameTypes(m_event.getDisplayName())));
		
		return FStream.from(attributes);
	}
	
	public static DefaultNodeFactory FACTORY = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			return new BasicEventElementNode((BasicEventElement)sme);
		}
	};
}
