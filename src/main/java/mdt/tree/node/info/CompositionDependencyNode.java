package mdt.tree.node.info;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.sm.info.CompositionDependency;
import mdt.model.sm.info.DefaultCompositionDependency;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.TerminalNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CompositionDependencyNode extends TerminalNode {
	public CompositionDependencyNode(CompositionDependency dep) {
		setTitle(dep.getDependencyType());
		setValue(String.format("%s -> %s", dep.getSourceId(), dep.getTargetId()));
	}

	public static CompositionDependencyNodeFactory FACTORY = new CompositionDependencyNodeFactory();
	public static class CompositionDependencyNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultCompositionDependency CompositionDependency = new DefaultCompositionDependency();
			CompositionDependency.updateFromAasModel(sme);
			
			return create(CompositionDependency);
		}
		
		public DefaultNode create(CompositionDependency dep) {
			return new CompositionDependencyNode(dep);
		}
	}
}
