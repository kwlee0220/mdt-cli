package mdt.cli.get;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import mdt.cli.RefExprs;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "parameter",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get parameter information of MDTInstance."
)
public class GetParameterCommand extends AbstractGetElementCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetParameterCommand.class);
	
	@Parameters(index="0", paramLabel="instance-id", description="MDTInstance id to show.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="parameter-id", defaultValue="*", description="Target parameter id")
	private String m_parameterId;
	
	public GetParameterCommand() {
		setLogger(s_logger);
	}

	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private ElementOutputType m_output = ElementOutputType.tree;

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		String elmRef = RefExprs.parameterRef(m_instanceId, m_parameterId);
		// 활성화는 AbstractGetElementCommand.run()이 한다.
		run(manager, ElementReferences.parseExpr(elmRef));
	}

	@Override
	protected void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception {
		printElement(smeRef, pw, m_output);
	}
}
