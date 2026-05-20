package mdt.tree.node.data;

import java.time.Instant;

import utils.func.FOption;

import mdt.aas.DataTypes;
import mdt.model.sm.data.Parameter;
import mdt.model.sm.data.ParameterValue;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactories;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
final class ParameterPairNodeFactory {
	public static DefaultNode create(Parameter param, ParameterValue paramValue) {
		DefaultNode node = DefaultNodeFactories.create(paramValue.getParameterValue());
		
		String idStr = param.getParameterId();
		String nameStr = FOption.mapOrElse(param.getParameterName(), n -> String.format("(%s)", n), "");
		String title = String.format("%s%s", idStr, nameStr);
		node.setTitle(title);

		boolean validLSL = param.getLSL() != null && param.getLSL().length() > 0;
		boolean validUSL = param.getUSL() != null && param.getUSL().length() > 0;
		String slStr = ( validLSL || validUSL )
						? String.format(", 공정범위: [%s-%s]", param.getLSL(), param.getUSL())
						: "";
		Instant ts = paramValue.getEventDateTime();
		String tsStr = FOption.mapOrElse(ts, t -> String.format(" (%s)", DataTypes.DATE_TIME.toValueString(t)), "");
		
		String value = String.format("%s%s%s", node.getValue(), slStr, tsStr);
		node.setValue(value);
		
		return node;
	}
}
