package mdt.tree.node;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface DefaultNodeFactory {
	public DefaultNode create(SubmodelElement element);
}
