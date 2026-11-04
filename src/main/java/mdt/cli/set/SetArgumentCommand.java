package mdt.cli.set;

import mdt.cli.OperationArgumentSpec;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
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
	description = "set Operation (AI/Simulation) argument value."
)
public class SetArgumentCommand extends AbstractSetCommand {
	@Mixin private OperationArgumentSpec m_target;

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		run(manager, ElementReferences.parseExpr(m_target.toReferenceExpr(manager)));
	}
}