package mdt.cli.run;

import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.ModelValidationException;
import mdt.model.SubmodelService;
import mdt.model.SubmodelService.Modifier;
import mdt.model.expr.MDTExpressionParser;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.task.builtin.AASOperationTaskCommand;
import mdt.task.builtin.HttpTaskCommand;
import mdt.task.builtin.ProgramTaskCommand;
import mdt.task.builtin.TaskUtils;
import mdt.workflow.model.TaskDescriptor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodel2",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "AI/Simulation Submodel execution command.",
	subcommands = {
		ProgramTaskCommand.class,
		HttpTaskCommand.class,
		AASOperationTaskCommand.class,
	}
)
public class RunSubmodelCommandOld extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RunSubmodelCommandOld.class);
	
	@Parameters(index="0", paramLabel="submodel-ref",
				description="target Submodel reference (<instance-id>:<submodel-idshort>)")
	private String m_opSmRefExpr;
	
	public RunSubmodelCommandOld() {
		setLogger(s_logger);
	}

	public void loadOperationSubmodel(MDTInstanceManager manager, TaskDescriptor descriptor)
		throws ModelValidationException {
		DefaultSubmodelReference opSmRef = MDTExpressionParser.parseSubmodelReference(m_opSmRefExpr).evaluate();
		opSmRef.activate(manager);
		descriptor.setSubmodelRef(opSmRef);
		
		SubmodelService svc = opSmRef.get();
		Submodel metadata = svc.getSubmodel(Modifier.METADATA);
		
		descriptor.setId(metadata.getIdShort());
        descriptor.addLabel(TaskUtils.LABEL_MDT_OPERATION, m_opSmRefExpr);
	}

	public static void main(String... args) throws Exception {
		main(new RunSubmodelCommandOld(), args);
		System.exit(0);
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
	}
}
