package mdt.tree.node.data;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.jetbrains.annotations.NotNull;

import utils.Preconditions;
import utils.stream.FStream;

import mdt.model.sm.SubmodelUtils;
import mdt.model.sm.data.DefaultEquipment;
import mdt.model.sm.data.DefaultOperation;
import mdt.model.sm.data.Parameter;
import mdt.model.sm.data.ParameterCollection;
import mdt.model.sm.data.ParameterValue;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.ListNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ParameterCollectionNode extends ListNode {
	protected ParameterCollection m_paramColl;
	
	public ParameterCollectionNode(@NotNull ParameterCollection coll) {
		Preconditions.checkNotNullArgument(coll, "coll is null");
		m_paramColl = coll;
		
		setTitle("Parameters");
	}
	
	@Override
	protected List<? extends DefaultNode> getElementNodes() {
		List<Parameter> params = m_paramColl.getParameterList();
		List<ParameterValue> values = m_paramColl.getParameterValueList();
		return FStream.from(params)
					.zipWith(FStream.from(values))
					.map(pair -> ParameterPairNodeFactory.create(pair._1, pair._2))
					.toList();
	}
	
	public static ParameterCollectionNodeFactory FACTORY = new ParameterCollectionNodeFactory();
	public static class ParameterCollectionNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			if ( SubmodelUtils.isEquipment(sme) ) {
				DefaultEquipment equip = new DefaultEquipment();
				equip.updateFromAasModel(sme);
				return new ParameterCollectionNode(equip);
			}
			else if ( SubmodelUtils.isAASOperation(sme) ) {
				DefaultOperation op = new DefaultOperation();
				op.updateFromAasModel(sme);
				return new ParameterCollectionNode(op);
			}
			else {
				throw new IllegalArgumentException("Invalid SubmodelElement type: " + sme.getClass().getName());
			}
		}
		
		public DefaultNode create(@NotNull ParameterCollection parameterCollection) {
			return new ParameterCollectionNode(parameterCollection);
		}
	}
}
