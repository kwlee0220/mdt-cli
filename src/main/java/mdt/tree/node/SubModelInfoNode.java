package mdt.tree.node;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.DefaultSubModelInfo;
import mdt.model.SubModelInfo;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class SubModelInfoNode extends TerminalNode {
	public SubModelInfoNode(SubModelInfo info) {
		setTitle("SubModelInfo");
		setValueType(String.format(" (%s)", info.getTitle()));
		
		String value = String.format("creator=%s, format=%s", info.getCreator(), info.getFormat());
		setValue(value);
	}

	public static SubModelInfoNodeFactory FACTORY = new SubModelInfoNodeFactory();
	public static class SubModelInfoNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultSubModelInfo SubModelInfo = new DefaultSubModelInfo();
			SubModelInfo.updateFromAasModel(sme);
			
			return create(SubModelInfo);
		}
		
		public DefaultNode create(SubModelInfo SubModelInfo) {
			return new SubModelInfoNode(SubModelInfo);
		}
	}
}