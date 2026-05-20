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
	private String m_id;

	@Parameters(index="1", arity="0..1", paramLabel="submodel-idshort",
				description="Target Submodel idshort to show (used with instance-id)")
	private String m_smIdShort = null;
	
	@Option(names={"--output", "-o"}, paramLabel="type", required=false,
			description="output type (candidnates: ${COMPLETION-CANDIDATES})")
	private OutputTypes m_output = OutputTypes.table;
	
	public GetSubmodelCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		SubmodelDescriptor smDesc;
		if ( m_smIdShort != null ) {
			MDTInstance instance = manager.getInstance(m_id);
			smDesc = Funcs.findFirst(instance.getAASSubmodelDescriptorAll(),
									desc -> desc.getIdShort().equals(m_smIdShort));
			if ( smDesc == null ) {
				throw new ResourceNotFoundException("Submodel",
													"instance-id=" + m_id + ", idShort=" + m_smIdShort);
			}
		}
		else {
			smDesc = mdt.getSubmodelRegistry().getSubmodelDescriptorById(m_id);
		}
		
		switch ( m_output ) {
			case table:
				displayAsTable(smDesc);
				break;
			case json:
				String smEp = DescriptorUtils.getEndpointString(smDesc.getEndpoints());
				if ( smEp == null ) {
					System.err.println("No endpoint info in the SubmodelDescriptor: " + smDesc.getId());
					System.exit(-1);
				}
				Submodel sm = AASUtils.newSubmodelService(smEp).getSubmodel();
				displaySubmodel(sm);
				break;
			case desc:
				displayAsJson(smDesc);
				break;
			default:
				System.err.println("Unknown output: " + m_output);
				System.exit(-1);
		}
	}

	public static final void main(String... args) throws Exception {
		main(new GetSubmodelCommand(), args);
	}
	
	private void displayAsJson(SubmodelDescriptor smDesc)
		throws SerializationException {
		JsonSerializer ser = new JsonSerializer();
		String jsonStr = ser.write(smDesc);
		System.out.println(jsonStr);
	}
	private void displaySubmodel(Submodel sm) {
		String jsonStr = MDTModelSerDe.toJsonString(sm);
		System.out.println(jsonStr);
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
		
		String endpoint = DescriptorUtils.getEndpointString(smDesc.getEndpoints());
		table.addCell(" ENDPOINT "); table.addCell(" " + getOrEmpty(endpoint));
		
		System.out.println(table.render());
	}
	
	private String getOrEmpty(Object obj) {
		return (obj != null) ? obj.toString() : "";
	}
}
