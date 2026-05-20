package mdt.cli.remove;

import org.slf4j.Logger;

import utils.LoggerNameBuilder;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "file",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Remove the AAS File attachment."
)
public class RemoveFileCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerNameBuilder.from(RemoveFileCommand.class)
															.dropSuffix(2)
															.append("unregister.mdt_instances")
															.getLogger();

	@Parameters(index="0", arity="1", paramLabel="element-ref", description="Target File SubmodelElement reference")
	private String m_elmRef = null;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new RemoveFileCommand(), args);
	}

	public RemoveFileCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		ElementReference smeRef = ElementReferences.parseExpr(m_elmRef);
		if ( smeRef instanceof MDTElementReference iref ) {
			iref.activate(manager);
		}
		
		smeRef.removeAttachment();
	}
}