package mdt.cli.set;

import mdt.cli.RefExprs;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "element",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set SubmodelElement value."
)
public class SetElementCommand extends AbstractSetCommand {
	@Parameters(index="0", paramLabel="instance-id", description="Target MDTInstance id.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="submodel-idShort", description="Target submodel idShort")
	private String m_smIdShort;
	
	@Parameters(index="2", paramLabel="idShortPath", description="Target SubmodelElement idShortPath")
	private String m_path;

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		String elmRef = RefExprs.elementRef(m_instanceId, m_smIdShort, m_path);
		run(manager, ElementReferences.parseExpr(elmRef));
	}

	public static void main(String... args) throws Exception {
		main(new SetElementCommand(), args);
	}
}