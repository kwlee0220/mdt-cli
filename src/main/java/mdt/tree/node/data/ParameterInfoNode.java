package mdt.tree.node.data;

import java.util.Objects;

import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.stream.FStream;

import mdt.model.sm.SubmodelUtils;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ParameterInfoNode extends TerminalNode {
	public ParameterInfoNode(SubmodelElement sme) {
		String id = getFieldStringOrNull(sme, "ParameterID");
		String name = getFieldStringOrNull(sme, "ParameterName");
		String title = (name != null && name.length() > 0) ? String.format("%s(%s)", name, id) : id;
		setTitle(title);
		
		String lsl = getFieldStringOrNull(sme, "LSL");
		String usl = getFieldStringOrNull(sme, "USL");
		String uomCode = getFieldStringOrNull(sme, "ParameterUOMCode");

		uomCode = (uomCode != null && uomCode.length() > 0) ? String.format("UOMCode(%s)", uomCode) : null;
		boolean validLSL = lsl != null && lsl.length() > 0;
		boolean validUSL = usl != null && usl.length() > 0;
		String slStr = ( validLSL || validUSL ) ? String.format("공정범위([%s-%s])", lsl, usl) : null;
		
		String value = FStream.of(uomCode, slStr)
						        .filter(Objects::nonNull)
						        .join(", ");
		setValue(value);
	}
	
	private String getFieldStringOrNull(SubmodelElement smc, String field) {
		Property prop = SubmodelUtils.findFieldById(smc, field, Property.class);
		return (prop != null) ? prop.getValue() : null;
	}
	
	public static ParameterInfoNodeFactory FACTORY = new ParameterInfoNodeFactory();
	public static class ParameterInfoNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			return new ParameterInfoNode(sme);
		}
	}
}
