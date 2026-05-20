package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.data.DefaultRouting;
import mdt.model.sm.data.Routing;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.SimpleListNode;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class RoutingNode extends TerminalNode {
	public RoutingNode(Routing routing) {
		setTitle("Routing");
		
		String value = String.format("%s, Item=%s setup=%s",
									routing.getRoutingName(), routing.getItemID(), routing.getSetupTime());
		setValue(value);
	}
	
	public static class RoutingListNode extends SimpleListNode<Routing> {
		public RoutingListNode(List<? extends Routing> elements) {
			super(elements, Routing -> FACTORY.create(Routing));
		}
	}

	public static RoutingNodeFactory FACTORY = new RoutingNodeFactory();
	public static class RoutingNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultRouting Routing = new DefaultRouting();
			Routing.updateFromAasModel(sme);
			
			return create(Routing);
		}
		
		public DefaultNode create(Routing Routing) {
			return new RoutingNode(Routing);
		}
	}
}