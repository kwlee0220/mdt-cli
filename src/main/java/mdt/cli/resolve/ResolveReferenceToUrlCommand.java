package mdt.cli.resolve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "reference",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Resolve ElementReference."
)
public class ResolveReferenceToUrlCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ResolveReferenceToUrlCommand.class);

	@Parameters(index="0", arity="1", paramLabel="element-ref", description="Target element reference")
	private String m_elmRef = null;

	public static final void main(String... args) throws Exception {
		main(new ResolveReferenceToUrlCommand(), args);
	}
	
	public ResolveReferenceToUrlCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		String requestUrl = manager.resolveReferenceToUrl(m_elmRef);
		System.out.println(requestUrl);
	}
}
