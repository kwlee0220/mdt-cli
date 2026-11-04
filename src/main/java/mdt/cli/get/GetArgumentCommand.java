package mdt.cli.get;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.OperationArgumentSpec;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "argument",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get operation argument of the MDTInstance."
)
public class GetArgumentCommand extends AbstractGetElementCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetArgumentCommand.class);
	
	@Mixin private OperationArgumentSpec m_target;
	
	public GetArgumentCommand() {
		setLogger(s_logger);
	}

	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private ElementOutputType m_output = ElementOutputType.tree;

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		ElementReference smeRef = ElementReferences.parseExpr(m_target.toReferenceExpr(manager));
		run(manager, smeRef);
	}

	@Override
	protected void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception {
		printElement(smeRef, pw, m_output);
	}
}
