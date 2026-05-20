package mdt.tree.node.info;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.stream.FStream;

import mdt.model.sm.info.DefaultTwinComposition;
import mdt.model.sm.info.TwinComposition;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.ListNode;
import mdt.tree.node.TerminalNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TwinCompositionNode extends ListNode {
	private TwinComposition m_twinCompo;

	public TwinCompositionNode(TwinComposition twinCompo) {
		m_twinCompo = twinCompo;
		setTitle("Twin Components");
	}

	@Override
	protected List<? extends DefaultNode> getElementNodes() {
		return FStream.from(m_twinCompo.getCompositionDependencies())
						.filter(dep -> dep.getDependencyType().equals("contains"))
						.map(dep -> TerminalNode.text(dep.getTargetId()))
						.toList();
	}
	
	public static DefaultNodeFactory FACTORY = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultTwinComposition twinCompo = new DefaultTwinComposition();
			twinCompo.updateAasModel(sme);
			
			return new TwinCompositionNode(twinCompo); 
		}
	};
}