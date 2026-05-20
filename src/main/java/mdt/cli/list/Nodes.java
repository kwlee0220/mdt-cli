package mdt.cli.list;

import java.util.Map;

import org.barfuin.texttree.api.Node;

import com.google.common.collect.Maps;

import utils.Keyed;
import utils.KeyedValueList;
import utils.stream.KeyValueFStream;

import mdt.client.instance.HttpMDTInstanceClient;
import mdt.model.instance.MDTInstanceStatus;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Nodes {
	private Nodes() {
		throw new AssertionError("Should not be called: class=" + Nodes.class.getName());
	}
	
	static boolean s_showEndpoint = false;
	
	static class RootNode implements Node {
		final Map<MDTInstanceStatus,StatusGroupNode> m_statusGroupNodes = Maps.newLinkedHashMap();
		
		RootNode() {
			m_statusGroupNodes.put(MDTInstanceStatus.RUNNING, new StatusGroupNode(MDTInstanceStatus.RUNNING));
			m_statusGroupNodes.put(MDTInstanceStatus.STARTING, new StatusGroupNode(MDTInstanceStatus.STARTING));
			m_statusGroupNodes.put(MDTInstanceStatus.STOPPING, new StatusGroupNode(MDTInstanceStatus.STOPPING));
			m_statusGroupNodes.put(MDTInstanceStatus.STOPPED, new StatusGroupNode(MDTInstanceStatus.STOPPED));
			m_statusGroupNodes.put(MDTInstanceStatus.FAILED, new StatusGroupNode(MDTInstanceStatus.FAILED));
		}
	
		public void addChild(InstanceNode child) {
			StatusGroupNode group = m_statusGroupNodes.get(child.getStatus());
			if ( group != null ) {
				group.addChild(child);
			}
		}
		
		public void removeChild(InstanceNode child) {
			StatusGroupNode group = m_statusGroupNodes.get(child.getStatus());
			group.removeChild(child.getId());
		}
		
		@Override
		public String getText() {
			return "MDT Instances";
		}
	
		@Override
		public Iterable<? extends Node> getChildren() {
			return m_statusGroupNodes.values();
		}
	}
	
	static class StatusGroupNode implements Node {
		private final MDTInstanceStatus m_status;
		private final Map<String,InstanceNode> m_instances = Maps.newLinkedHashMap();
		
		StatusGroupNode(MDTInstanceStatus status) {
			m_status = status;
		}
	
		public void addChild(InstanceNode child) {
			m_instances.put(child.getId(), child);
		}
		
		public void removeChild(String id) {
			m_instances.remove(id);
		}
		
		@Override
		public String getText() {
			return ANSI_BOLD + m_status.name() + ANSI_RESET;
		}
		
//		@Override
//		public NodeColor getColor() {
//			return NodeColor.LightGreen;
//		}
	
		@Override
		public Iterable<? extends Node> getChildren() {
			return m_instances.values();
		}
		
		@Override
		public String toString() {
			String memberIdsStr = KeyValueFStream.from(m_instances)
											.map(kv -> kv.value().getId())
											.join(", ");
			return String.format("%s: {%s}", m_status, memberIdsStr);
		}
	}
	
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BOLD = "\u001B[1m";
	public static class InstanceNode implements Node, Keyed<String> {
		private final HttpMDTInstanceClient m_instance;
		private final MDTInstanceStatus m_status;
		private final String m_baseUrl;
		private final KeyedValueList<String, InstanceNode> m_children;
		
		public InstanceNode(HttpMDTInstanceClient instance) {
			m_instance = instance;
			m_status = instance.getStatus();
			m_baseUrl = (m_status == MDTInstanceStatus.RUNNING) ? m_instance.getServiceEndpoint() : null;
			m_children = KeyedValueList.with(Node::getKey);
		}

		@Override
		public String key() {
			return getId();
		}
		
		public String getId() {
			return m_instance.getId();
		}
		
		public HttpMDTInstanceClient getInstance() {
			return m_instance;
		}
		
		public MDTInstanceStatus getStatus() {
			return m_status;
		}
		
		@Override
		public String getText() {
			String urlStr = ( s_showEndpoint && m_baseUrl != null)
							? String.format(" (%s)", m_baseUrl) : "";
			return m_instance.getId() + urlStr;
		}
	
		@Override
		public Iterable<? extends Node> getChildren() {
			return m_children;
		}
	
		public void addChild(InstanceNode child) {
			m_children.add(child);
		}
		
		@Override
		public String toString() {
			return m_instance.getId();
		}
	}
}
