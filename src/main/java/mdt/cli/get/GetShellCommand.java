package mdt.cli.get;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.Key;
import org.eclipse.digitaltwin.aas4j.v3.model.LangStringNameType;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Try;
import utils.stream.FStream;

import mdt.model.instance.MDTInstance;
import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.AssetAdministrationShellService;
import mdt.model.DescriptorUtils;
import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.ResourceNotFoundException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "shell",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get AssetAdministrationShell information."
)
public class GetShellCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetShellCommand.class);

	@Parameters(index="0", arity="1", paramLabel="aas-id|instance-id",
				description="AssetAdministrationShell id (or its idShort, or MDTInstance id) to show")
	private String m_target = null;

	enum OutputTypes { table, json, desc };
	@Option(names={"--output", "-o"}, paramLabel="type", required=false,
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private OutputTypes m_output = OutputTypes.table;

	public static final void main(String... args) throws Exception {
		main(new GetShellCommand(), args);
	}
	
	public GetShellCommand() {
		setLogger(s_logger);
	}
	
	private HttpMDTInstanceClient getUniqueInstanceByAasIdShort(HttpMDTInstanceManager manager, String aasIdShort) {
		List<MDTInstance> instList = manager.getInstanceAllByAasIdShort(aasIdShort);
		if ( instList.size() == 1 ) {
			return (HttpMDTInstanceClient)instList.get(0);
		}
		else  {
			throw new ResourceNotFoundException("AssetAdministrationShell", "idShort=" + aasIdShort);
		}
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		HttpMDTInstanceClient instance = Try.of(() -> manager.getInstanceByAasId(m_target))
											.recover(() -> getUniqueInstanceByAasIdShort(manager, m_target))
											.recover(() -> manager.getInstance(m_target))
											.get();
		switch ( m_output ) {
			case desc:
				displayDescriptor(instance);
				break;
			case json:
				displayAsJson(instance);
				break;
			case table:
				displayAsTable(instance);
				break;
			default:
				throw new AssertionError("unknown output: " + m_output);
		}
	}
	
	private void displayAsJson(HttpMDTInstanceClient instance) throws SerializationException {
		AssetAdministrationShellService aasSvc = instance.getAssetAdministrationShellService();
		AssetAdministrationShell aas = aasSvc.getAssetAdministrationShell();
		out().println(MDTModelSerDe.toJsonString(aas));
	}
	
	private void displayDescriptor(HttpMDTInstanceClient instance) throws SerializationException {
		AssetAdministrationShellDescriptor desc = instance.getAASShellDescriptor();
		out().println(MDTModelSerDe.toJsonString(desc));
	}
	
	private void displayAsTable(HttpMDTInstanceClient instance) {
		AssetAdministrationShellService aasSvc = instance.getAssetAdministrationShellService();
		AssetAdministrationShell aas = aasSvc.getAssetAdministrationShell();
		
		Table table = new Table(2);
		table.addCell(" FIELD "); table.addCell(" VALUE");
		table.addCell(" ID "); table.addCell(" " + aas.getId());
		table.addCell(" ID_SHORT "); table.addCell(" " + getOrEmpty(aas.getIdShort()) + " ");
		
		table.addCell(" GLOBAL_ASSET_ID ");
			table.addCell(" " + getOrEmpty(aas.getAssetInformation().getGlobalAssetId()) + " ");
		table.addCell(" INSTANCE "); table.addCell(" " + instance.getId());
		
		List<LangStringNameType> names = aas.getDisplayName();
		if ( names != null ) {
			String displayName = FStream.from(names)
										.map(name -> name.getText())
										.join(". ");
			table.addCell(" DISPLAY_NAME "); table.addCell(" " + displayName);
		}
		else {
			table.addCell(" DISPLAY_NAME "); table.addCell("");
		}
		
		FStream.from(aas.getSubmodels())
				.flatMapIterable(Reference::getKeys)
				.map(Key::getValue)
				.zipWithIndex()
				.forEach(tup -> {
					table.addCell(String.format(" SUB_MODEL_REF_[%02d] ", tup.index()));
					table.addCell(" " + tup.value() + " ");
				});
		String aasEp = DescriptorUtils.toAASServiceEndpointString(instance.getServiceEndpoint(), aas.getId());
		table.addCell(" ENDPOINT "); table.addCell(" " + getOrEmpty(aasEp));
		
		out().println(table.render());
	}
	
	private static String getOrEmpty(Object obj) {
		return (obj != null) ? obj.toString() : "";
	}
}
