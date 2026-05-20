package mdt.tree.node.timeseries;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.stream.FStream;

import mdt.model.timeseries.DefaultMetadata;
import mdt.model.timeseries.DefaultRecord;
import mdt.model.timeseries.Record.FieldValue;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.DefaultNodeFactory;
import mdt.tree.node.TerminalNode;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class TimeseriesSubmodelNodeFactories {
	private TimeseriesSubmodelNodeFactories() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	public static class RecordTransform implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultRecord rec = new DefaultRecord();
			rec.updateFromAasModel(sme);
			
			TerminalNode node = new TerminalNode();
			node.setTitle(rec.getIdShort());
			node.setValueType(" (REC)");
			
			String csv = FStream.from(rec.getFieldValues())
								.map(this::toString)
								.join(", ");
			node.setValue(csv);
			
			return node;
		}
		
		private String toString(FieldValue fv) {
			return String.format("%s:%s", fv.getField().getName(), fv.getValue());
		}
	}
	
	public static class MetadataNodeFactory implements DefaultNodeFactory {
		@Override
		public DefaultNode create(SubmodelElement sme) {
			DefaultMetadata meta = new DefaultMetadata();
			meta.updateFromAasModel(sme);

			TerminalNode node = new TerminalNode();
			String csv = FStream.from(meta.getRecord().getFieldAll())
								.map(field -> String.format("%s:%s", field.getName(), field.getType()))
								.join(", ");
			node.setTitle(meta.getIdShort());
			node.setValueType(" (METADATA)");
			node.setValue(csv);
			
			return node;
		}
	}
}

