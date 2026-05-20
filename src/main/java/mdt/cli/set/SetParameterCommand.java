package mdt.cli.set;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "parameter",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set Parameter value."
)
public class SetParameterCommand extends AbstractSetCommand {
	@Parameters(index="0", paramLabel="id", description="MDTInstance id to show.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="parameter", defaultValue="*", description="Target parameter id")
	private String m_parameterId;

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		String elmRef = String.format("param:%s:%s", m_instanceId, m_parameterId);
		ElementReference smeRef = ElementReferences.parseExpr(elmRef);
		
		run(manager, smeRef);
	}
}