package mdt.cli.get;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Funcs;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.AASUtils;
import mdt.model.DescriptorUtils;
import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.ReferenceUtils;
import mdt.model.ResourceNotFoundException;
import mdt.model.instance.MDTInstance;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodel",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get Submodel information."
)
public class GetSubmodelCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetSubmodelCommand.class);
	
	enum OutputTypes { table, json, desc };

	@Parameters(index="0", arity="1", paramLabel="instance-id|submodel-id",
				description="Target MDTInstance id or Submodel id to show")
	private String m_instanceOrSubmodelId;

	@Parameters(index="1", arity="0..1", paramLabel="submodel-idShort",
				description="Target Submodel idShort to show (used with instance-id)")
	private String m_smIdShort = null;
	
	@Option(names={"--output", "-o"}, paramLabel="type", required=false,
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private OutputTypes m_output = OutputTypes.table;
	
	public GetSubmodelCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		SubmodelDescriptor smDesc;
		if ( m_smIdShort != null ) {
			MDTInstance instance = manager.getInstance(m_instanceOrSubmodelId);
			smDesc = Funcs.findFirst(instance.getAASSubmodelDescriptorAll(),
									desc -> m_smIdShort.equals(desc.getIdShort()));
			if ( smDesc == null ) {
				throw new ResourceNotFoundException("Submodel",
													"instance-id=" + m_instanceOrSubmodelId + ", idShort=" + m_smIdShort);
			}
		}
		else {
			smDesc = mdt.getSubmodelRegistry().getSubmodelDescriptorById(m_instanceOrSubmodelId);
		}
		
		switch ( m_output ) {
			case table:
				displayAsTable(smDesc);
				break;
			case json:
				String smEp = DescriptorUtils.getEndpointString(smDesc.getEndpoints());
				if ( smEp == null ) {
					err().println("No endpoint info in the SubmodelDescriptor: " + smDesc.getId());
					markFailed();
					return;
				}
				Submodel sm = AASUtils.newSubmodelService(smEp).getSubmodel();
				displaySubmodel(sm);
				break;
			case desc:
				displayDescriptor(smDesc);
				break;
			default:
				throw new AssertionError("unknown output: " + m_output);
		}
	}

	public static final void main(String... args) throws Exception {
		main(new GetSubmodelCommand(), args);
	}
	
	private void displayDescriptor(SubmodelDescriptor smDesc)
		throws SerializationException {
		JsonSerializer ser = new JsonSerializer();
		String jsonStr = ser.write(smDesc);
		out().println(jsonStr);
	}
	private void displaySubmodel(Submodel sm) {
		String jsonStr = MDTModelSerDe.toJsonString(sm);
		out().println(jsonStr);
	}
	
	private void displayAsTable(SubmodelDescriptor smDesc) {
		Table table = new Table(2);

		table.addCell(" FIELD "); table.addCell(" VALUE");
		table.addCell(" ID "); table.addCell(" " + smDesc.getId());
		table.addCell(" ID_SHORT "); table.addCell(" " + getOrEmpty(smDesc.getIdShort()) + " ");
		
		table.addCell(" SEMANTIC_ID ");
		String semanticId = ReferenceUtils.getSemanticIdStringOrNull(smDesc.getSemanticId());
		if ( semanticId != null ) {
			String msg = String.format(" (%s) %s", ReferenceUtils.getShortSubmodelSemanticId(semanticId), semanticId);
			table.addCell(msg);
		}
		else {
			// 값 칸을 비워 두어야 다음 행이 한 칸씩 밀리지 않는다.
			table.addCell("");
		}
		
		String endpoint = DescriptorUtils.getEndpointString(smDesc.getEndpoints());
		table.addCell(" ENDPOINT "); table.addCell(" " + getOrEmpty(endpoint));
		
		out().println(table.render());
	}
	
	private String getOrEmpty(Object obj) {
		return (obj != null) ? obj.toString() : "";
	}
}
