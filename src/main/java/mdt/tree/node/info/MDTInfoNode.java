package mdt.tree.node.info;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.func.Optionals;

import mdt.model.sm.info.DefaultMDTInfo;
import mdt.model.sm.info.MDTInfo;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTInfoNode extends TerminalNode {
	public MDTInfoNode(MDTInfo mdtInfo) {
		setTitle(mdtInfo.getIdShort());
		
		String statusStr = "" + Optionals.getOrElse(mdtInfo.getStatus(), "N/A");
		String value = String.format("name=\"%s\", type=%s, status=%s",
										mdtInfo.getAssetName(), mdtInfo.getAssetType(), statusStr);
		setValue(value);
	}

	public static MDTInfoNodeFactory FACTORY = new MDTInfoNodeFactory();
	public static class MDTInfoNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultMDTInfo mdtInfo = new DefaultMDTInfo();
			mdtInfo.updateFromAasModel(sme);
			
			return create(mdtInfo);
		}
		
		public DefaultNode create(MDTInfo mdtInfo) {
			return new MDTInfoNode(mdtInfo);
		}
	}
}