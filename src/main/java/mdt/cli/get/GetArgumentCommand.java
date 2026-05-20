package mdt.cli.get;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Funcs;
import utils.stream.FStream;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTOperationDescriptor;
import mdt.model.instance.MDTOperationDescriptor.ArgumentDescriptor;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

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
	
	@Parameters(index="0", paramLabel="id", description="MDTInstance id to show.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="operation", description="Target operation (AI or Simulation) id")
	private String m_opId;
	
	@Parameters(index="2", paramLabel="argument", description="Target argument id")
	private String m_argumentId;
	
	public GetArgumentCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		MDTInstance inst = manager.getInstance(m_instanceId);
		MDTOperationDescriptor op
				= Funcs.findFirst(inst.getMDTOperationDescriptorAll(), o -> o.getId().equals(m_opId));
		if ( op == null ) {
			throw new IllegalArgumentException("No such operation id=" + m_opId
                                                + " in the instance " + m_instanceId);
		}
		String elmRef = FStream.from(op.getOutputArguments())
								.findFirst(arg -> arg.getId().equals(m_argumentId))
								.map(ArgumentDescriptor::getReference)
								.getOrNull();
		if ( elmRef == null ) {
			elmRef = FStream.from(op.getInputArguments())
							.findFirst(arg -> arg.getId().equals(m_argumentId))
							.map(ArgumentDescriptor::getReference)
			                .getOrThrow(() -> new IllegalArgumentException("No such argument id=" + m_argumentId
	                                + " in the operation " + m_opId));
		}
		
		ElementReference smeRef = ElementReferences.parseExpr(elmRef);
		run(manager, smeRef);
	}
}
