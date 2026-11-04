package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import utils.KeyValue;
import utils.Throwables;
import utils.stream.KeyValueFStream;

import mdt.cli.RefExprs;
import mdt.model.expr.MDTParserException;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.SubmodelUtils;
import mdt.model.sm.SubmodelUtils.OperationSubmodelDescriptor;
import mdt.model.sm.ref.DefaultSubmodelReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.task.AbstractOperationTask;
import mdt.task.OperationSubmodelTask;
import mdt.task.TaskArgument;
import mdt.task.TaskArgument.ReferenceArgument;


/**
 * 연산(Operation) Submodel이 제공하는 AAS Operation을 호출하는 커맨드.
 * <p>
 * {@code <instance-id>:<submodel-idShort>}로 지정된 Submodel의 연산을
 * {@link OperationSubmodelTask}로 실행한다. 태스크 인자는 {@code --in.<name>=<expr>},
 * {@code --out.<name>=<ref-expr>} 옵션으로 지정하며, 인자 이름은 Submodel의 인자 기술자에 선언된
 * 이름과 일치해야 한다. {@link OperationSubmodelTask}는 inoutput 인자를 지원하지 않으므로
 * {@code --inout.<name>}이 지정되면 오류가 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodel",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "run an AI/Simulation Submodel."
)
public class RunOperationSubmodelCommand extends AbstractRunTaskCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RunOperationSubmodelCommand.class);

	@Parameters(index="0", paramLabel="id", description="MDTInstance id.")
	private String m_instanceId;

	@Parameters(index="1", paramLabel="submodel-idShort",
				description="Target AI/Simulation submodel idShort")
	private String m_submodelIdShort;

	public RunOperationSubmodelCommand() {
		setLogger(s_logger);
	}

	@Override
	protected AbstractOperationTask newTask(MDTInstanceManager manager, TaskCommandArguments tad,
											Duration pollInterval, Duration timeout) {
		// OperationSubmodelTask는 inoutput argument를 지원하지 않으므로,
		// inoutput argument가 지정된 경우 오류를 발생시킨다.
		if ( tad.getInoutputs().size() > 0 ) {
			throw new IllegalArgumentException("inoutput argument is not supported for OperationSubmodelTask");
		}

		String smRefStr = RefExprs.submodelRef(m_instanceId, m_submodelIdShort);
		DefaultSubmodelReference smRef = ElementReferences.parseSubmodelExpr(smRefStr);

		// Submodel의 인자 기술자 로딩한다.
		smRef.activate(manager);
		Submodel opSubmodel = smRef.get().getSubmodel();
		OperationSubmodelDescriptor opSmDesc = SubmodelUtils.loadOperationSubmodelDescriptor(opSubmodel);

		Map<String,TaskArgument> inputTaskArgs = loadInputTaskArguments(opSmDesc, tad);
		Map<String,ReferenceArgument> outputTaskArgs = loadOutputTaskArguments(opSmDesc, tad);

		return OperationSubmodelTask.builder()
									.submodelReference(smRef)
									.inputArguments(inputTaskArgs)
									.outputArguments(outputTaskArgs)
									.pollInterval(pollInterval)
									.timeout(timeout)
									.build();
	}

	@Override
	protected String getTargetString() {
		return String.format("Submodel[%s:%s]", m_instanceId, m_submodelIdShort);
	}

	private Map<String,TaskArgument> loadInputTaskArguments(OperationSubmodelDescriptor smDesc,
															TaskCommandArguments cmdArgs) {
		// Submodel에 선언되지 않은 이름의 인자는 아래 match()에서 조용히 무시되기 때문에 미리 확인한다.
		checkArgumentNames("input", cmdArgs.getInputs(), smDesc.getInputs().keySet());

		return KeyValueFStream.from(smDesc.getInputs())
								.match(cmdArgs.getInputs())
								.mapKeyValue((argId, match) -> {
									SubmodelElement argSme = match._1.getSubmodelElement();
									String argSpec = match._2;

									try {
										MDTElementReference ref = ElementReferences.parseExpr(argSpec);
										return KeyValue.of(argId, TaskArgument.reference(ref));
									}
									catch ( MDTParserException expected ) {
										// 참조 표현식이 아닌 경우는 인자 타입에 맞는 리터럴로 해석한다.
										try {
											return KeyValue.of(argId, parseLiteralArgument(argSme, argSpec));
										}
										catch ( IOException e ) {
											return Throwables.sneakyThrow(e);
										}
									}
								})
								.toMap();
	}

	private Map<String,ReferenceArgument> loadOutputTaskArguments(OperationSubmodelDescriptor smDesc,
																	TaskCommandArguments cmdArgs) {
		// Submodel에 선언되지 않은 이름의 인자는 아래 match()에서 조용히 무시되기 때문에 미리 확인한다.
		checkArgumentNames("output", cmdArgs.getOutputs(), smDesc.getOutputs().keySet());

		return KeyValueFStream.from(smDesc.getOutputs())
								.match(cmdArgs.getOutputs())
								.mapKeyValue((argId, match) -> KeyValue.of(argId,
																		TaskArgument.reference(match._2)))
								.toMap();
	}

	public static void main(String... args) throws Exception {
		main(new RunOperationSubmodelCommand(), args);
		System.exit(0);
	}
}
