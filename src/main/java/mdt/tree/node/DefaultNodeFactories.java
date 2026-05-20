package mdt.tree.node;

import java.util.List;
import java.util.Map;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.MultiLanguageProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import utils.func.FOption;
import utils.func.Optionals;
import utils.stream.FStream;

import mdt.model.Input;
import mdt.model.Output;
import mdt.model.ReferenceUtils;
import mdt.model.sm.data.Equipment;
import mdt.model.sm.data.Parameter;
import mdt.model.sm.data.ParameterValue;
import mdt.model.sm.info.CompositionDependency;
import mdt.model.sm.info.CompositionItem;
import mdt.model.sm.info.MDTInfo;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;
import mdt.model.sm.value.MultiLanguagePropertyValue;
import mdt.model.timeseries.Metadata;
import mdt.model.timeseries.Record;
import mdt.tree.node.data.ParameterInfoNode;
import mdt.tree.node.data.ParameterValueNode;
import mdt.tree.node.info.CompositionDependencyNode;
import mdt.tree.node.info.CompositionItemNode;
import mdt.tree.node.info.MDTInfoNode;
import mdt.tree.node.op.InputArgumentNode;
import mdt.tree.node.op.OutputArgumentNode;
import mdt.tree.node.timeseries.TimeseriesSubmodelNodeFactories.MetadataNodeFactory;
import mdt.tree.node.timeseries.TimeseriesSubmodelNodeFactories.RecordTransform;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultNodeFactories {
	private DefaultNodeFactories() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	public static DefaultNodeFactory SMC_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			return new SMCNode((SubmodelElementCollection)element);
		} 
	};
	
	private static final Map<String,DefaultNodeFactory> FACTORY_REGISTRY = Maps.newHashMap();
	static {
		FACTORY_REGISTRY.put(MDTInfo.SEMANTIC_ID, MDTInfoNode.FACTORY);
		FACTORY_REGISTRY.put(ParameterValue.SEMANTIC_ID, ParameterValueNode.FACTORY);
		FACTORY_REGISTRY.put(Parameter.SEMANTIC_ID, ParameterInfoNode.FACTORY);
		FACTORY_REGISTRY.put(Equipment.SEMANTIC_ID, SMC_FACT);
		FACTORY_REGISTRY.put(CompositionItem.SEMANTIC_ID, CompositionItemNode.FACTORY);
		FACTORY_REGISTRY.put(CompositionDependency.SEMANTIC_ID, CompositionDependencyNode.FACTORY);
		FACTORY_REGISTRY.put(Input.SEMANTIC_ID, InputArgumentNode.FACTORY);
		FACTORY_REGISTRY.put(Output.SEMANTIC_ID, OutputArgumentNode.FACTORY);
		FACTORY_REGISTRY.put(Record.SEMANTIC_ID, new RecordTransform());
		FACTORY_REGISTRY.put(Metadata.SEMANTIC_ID, new MetadataNodeFactory());
	}
	
	public static DefaultNodeFactory getNodeFactory(String semanticId) {
		return FACTORY_REGISTRY.get(semanticId);
	}
	
	public static DefaultNode create(SubmodelElement smElm) {
		Preconditions.checkArgument(smElm != null,
									"SubmodelElement is not allowed to be null in DefaultNodeFactories");
		
		String semanticIdStr = ReferenceUtils.getSemanticIdStringOrNull(smElm.getSemanticId());
		DefaultNodeFactory fact = FACTORY_REGISTRY.get(semanticIdStr);
		if ( fact != null ) {
			return fact.create(smElm);
		}
		
		if ( smElm instanceof Property prop ) {
			return PROPERTY_FACT.create(prop);
		}
		else if ( smElm instanceof File aasFile ) {
			return FILE_FACT.create(aasFile);
		}
		else if ( smElm instanceof SubmodelElementCollection smc) {
			return SMC_FACT.create(smc);
		}
		else if ( smElm instanceof SubmodelElementList sml) {
			return SML_FACT.create(sml);
		}
		else if ( smElm instanceof Operation op) {
			return OP_FACT.create(op);
		}
		else if ( smElm instanceof MultiLanguageProperty mlp ) {
			return MLP_FACT.create(mlp);
		}
		else {
			return JSON_FACT.create(smElm);
		}
	}
	
	private static DefaultNodeFactory PROPERTY_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			Property prop = (Property)element;
			
			String title = prop.getIdShort();
//			String title = Funcs.getFirst(prop.getDisplayName())
//								 .map(nt -> String.format("%s(%s)", prop.getIdShort(), nt.getText()))
//								 .getOrElse(prop.getIdShort());
			String typeStr = FOption.map(prop.getValueType(), vt -> String.format(" (%s)", vt));
			return new TerminalNode(title, typeStr, prop.getValue());
		} 
	};
	
	private static DefaultNodeFactory FILE_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			File aasFile = (File)element;
			String path = Optionals.getOrElse(aasFile.getValue(), "None");
			String value = String.format("%s (%s)", path, aasFile.getContentType());
			return new TerminalNode(aasFile.getIdShort(), " (File)", value);
		} 
	};
	
	private static DefaultNodeFactory SML_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			return new SMLNode((SubmodelElementList)element);
		} 
	};
	
	private static DefaultNodeFactory OP_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			return new AASOperationNode((Operation)element);
		} 
	};
	
	private static class SMCNode extends DefaultNode {
		private List<? extends DefaultNode> m_children;

		public SMCNode(SubmodelElementCollection smc) {
			setTitle(smc.getIdShort());
			setValueType(" (SMC)");
			setValue("");
			
			m_children = FStream.from(smc.getValue())
								.map(elm -> DefaultNodeFactories.create(elm))
								.toList();
		}

		@Override
		public Iterable<? extends Node> getChildren() {
			return m_children;
		}
	}
	
	private static class SMLNode extends ListNode {
		private final SubmodelElementList m_sml;

		public SMLNode(SubmodelElementList sml) {
			setTitle(sml.getIdShort());
			setValueType(" (SML)");
			m_sml = sml;
		}

		@Override
		protected List<? extends DefaultNode> getElementNodes() {
			return FStream.from(m_sml.getValue())
							.map(elm -> DefaultNodeFactories.create(elm))
							.toList();
		}
	}
	
	private static DefaultNodeFactory MLP_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			MultiLanguageProperty mlp = (MultiLanguageProperty)element;
			
			MultiLanguagePropertyValue mlpv = MultiLanguagePropertyValue.from(mlp);
			List<String> str = FStream.from(mlpv.getLangTextAll())
						                .map(t -> String.format("%s:%s", t.getLanguage(), t.getText()))
						                .toList();
			
			return new TerminalNode(mlp.getIdShort(), " (MLP)", str);
		} 
	};
	
	private static DefaultNodeFactory JSON_FACT = new DefaultNodeFactory() {
		@Override
		public DefaultNode create(SubmodelElement element) {
			ElementValue value = ElementValues.getValue(element);
			String valStr = (value != null) ? value.toDisplayString() : "null"; 
			
			return new TerminalNode(element.getIdShort(), " (ValueOnlySerialization)", valStr);
		} 
	};
}
