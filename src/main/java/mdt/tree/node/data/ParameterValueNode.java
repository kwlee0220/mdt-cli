package mdt.tree.node.data;

import java.time.Instant;

import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.aas.DataTypes;
import mdt.model.sm.SubmodelUtils;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;
import mdt.tree.node.DefaultNodeFactory;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ParameterValueNode {
	private ParameterValueNode() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	public static ParameterValueNodeFactory FACTORY = new ParameterValueNodeFactory();
	public static class ParameterValueNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			SubmodelElement value = SubmodelUtils.traverse(sme, "ParameterValue");
			DefaultNode node = DefaultNodeFactories.create(value);

			String id = getFieldStringOrNull(sme, "ParameterID");
			node.setTitle(id);

			Property prop = SubmodelUtils.findFieldById(sme, "EventDateTime", Property.class);
			String tsStr;
			if ( prop != null ) {
				Instant ts = DataTypes.DATE_TIME.parseValueString(prop.getValue());
				tsStr = String.format(" (ts=%s)", DataTypes.DATE_TIME.toValueString(ts));
			}
			else {
				tsStr = "";
			}
			node.setValue(node.getValue() + tsStr);
			return node;
		}
		
		private String getFieldStringOrNull(SubmodelElement smc, String field) {
			Property prop = SubmodelUtils.findFieldById(smc, field, Property.class);
			return ( prop != null ) ? prop.getValue() : null;
		}
	}
}
