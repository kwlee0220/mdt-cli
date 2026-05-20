package mdt.tree;

import java.io.IOException;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;

import utils.Tuple;

import mdt.model.instance.MDTInstance;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.model.sm.ref.MDTArgumentKind;
import mdt.model.sm.ref.MDTArgumentReference;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTModelInfoNodes {
	
	private static Tuple<List<SubmodelElement>, List<SubmodelElement>>
	readMDTOperationArgumentValueAll(MDTInstance instance, String opName) throws IOException {
		DefaultSubmodelReference submodelRef = DefaultSubmodelReference.ofIdShort(instance.getId(), opName);
		
		MDTArgumentReference inArgsRef = MDTArgumentReference.builder()
															.submodelReference(submodelRef)
															.kind(MDTArgumentKind.INPUT)
															.argument("*")
															.build();
		inArgsRef.activate(instance.getInstanceManager());
		List<SubmodelElement> inArgSmeList = ((SubmodelElementList)inArgsRef.read()).getValue();
		
		MDTArgumentReference outArgsRef = MDTArgumentReference.builder()
																.submodelReference(submodelRef)
																.kind(MDTArgumentKind.OUTPUT)
																.argument("*")
																.build();
		outArgsRef.activate(instance.getInstanceManager());
		List<SubmodelElement> outArgSmeList = ((SubmodelElementList)outArgsRef.read()).getValue();
		
		return Tuple.of(inArgSmeList, outArgSmeList);
    }
}
