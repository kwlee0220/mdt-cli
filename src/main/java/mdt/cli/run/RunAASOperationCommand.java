package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import utils.KeyValue;
import utils.Throwables;
import utils.stream.FStream;

import mdt.cli.RefExprs;
import mdt.model.expr.MDTParserException;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.task.AASOperationTask;
import mdt.task.AbstractOperationTask;
import mdt.task.TaskArgument;
import mdt.task.TaskArgument.ReferenceArgument;


/**
 * 참조가 가리키는 AAS Operation element를 호출하는 커맨드.
 * <p>
 * {@code <instance-id>:<submodel-idShort>:<path>}로 지정된 연산 element를
 * {@link AASOperationTask}로 실행한다. 태스크 인자는 {@code --in.<name>=<expr>},
 * {@code --out.<name>=<ref-expr>}, {@code --inout.<name>=<ref-expr>} 옵션으로 지정하며,
 * 인자 이름은 대상 연산에 선언된 OperationVariable의 idShort와 일치해야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "operation",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "run an AAS Operation element."
)
public class RunAASOperationCommand extends AbstractRunTaskCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RunAASOperationCommand.class);

	@Parameters(index="0", paramLabel="instance-id", description="MDTInstance id.")
	private String m_instanceId;

	@Parameters(index="1", paramLabel="submodel-idShort", description="Target submodel idShort")
	private String m_submodelIdShort;

	@Parameters(index="2", paramLabel="idShortPath", description="Target Operation element idShortPath")
	private String m_path;

	public RunAASOperationCommand() {
		setLogger(s_logger);
	}

	@Override
	protected AbstractOperationTask newTask(MDTInstanceManager manager, TaskCommandArguments cmdArgs,
											Duration pollInterval, Duration timeout) {
		String opRefStr = RefExprs.elementRef(m_instanceId, m_submodelIdShort, m_path);
		MDTElementReference opRef = ElementReferences.parseExpr(opRefStr);

		// 일부 태스크 인자의 값이 문자열인 경우, AAS OperationVariable의 데이터 타입에 맞게 변환하여
		// TaskArgument에 다시 설정한다.
		try {
			opRef.activate(manager);
			Operation opSme = (Operation)opRef.read();

			Map<String,TaskArgument> inputArgs = loadInputTaskArguments(opSme, cmdArgs.getInputs());
			Map<String,ReferenceArgument> outputArgs
					= loadReferenceExprArguments("output", opSme.getOutputVariables(), cmdArgs.getOutputs());
			Map<String,ReferenceArgument> inoutputArgs
					= loadReferenceExprArguments("inoutput", opSme.getInoutputVariables(), cmdArgs.getInoutputs());

			return AASOperationTask.builder()
									.operation(opRef)
									.inputArguments(inputArgs)
									.inoutputArguments(inoutputArgs)
									.outputArguments(outputArgs)
									.pollInterval(pollInterval)
									.timeout(timeout)
									.build();
		}
		catch ( IOException e ) {
			throw new IllegalArgumentException("failed to read Operation element: " + opRef, e);
		}
	}

	@Override
	protected String getTargetString() {
		return String.format("Operation[%s:%s:%s]", m_instanceId, m_submodelIdShort, m_path);
	}

	private Map<String,TaskArgument> loadInputTaskArguments(Operation opSme, Map<String,String> inputCmdArgs) {
		// 연산에 선언되지 않은 이름의 인자는 아래 match()에서 조용히 무시되기 때문에 미리 확인한다.
		checkArgumentNames("input", inputCmdArgs, toVariableNames(opSme.getInputVariables()));

		return FStream.from(opSme.getInputVariables())
						.tagKey(opv -> opv.getValue().getIdShort())
						.match(inputCmdArgs)
						.mapKeyValue((argId, match) -> KeyValue.of(argId, toInputTaskArgument(match._1.getValue(), match._2)))
						.toMap();
	}

	private Map<String,ReferenceArgument> loadReferenceExprArguments(String kind, List<OperationVariable> opVars,
																	Map<String,String> cmdArgs) {
		// 연산에 선언되지 않은 이름의 인자는 아래 match()에서 조용히 무시되기 때문에 미리 확인한다.
		checkArgumentNames(kind, cmdArgs, toVariableNames(opVars));

		return FStream.from(opVars)
						.tagKey(opv -> opv.getValue().getIdShort())
						.match(cmdArgs)
						.mapKeyValue((argId, match) -> {
							String refStr = match._2;
							try {
								ReferenceArgument taskArg = TaskArgument.reference(refStr);
								return KeyValue.of(argId, taskArg);
							}
							catch ( MDTParserException e ) {
								s_logger.error("invalid {} reference expr: arg={}, ref={}", kind, argId, refStr, e);
								return Throwables.sneakyThrow(e);
							}
						})
						.toMap();
	}

	private static Set<String> toVariableNames(List<OperationVariable> opVars) {
		return FStream.from(opVars)
						.map(opv -> opv.getValue().getIdShort())
						.toSet();
	}

	public static void main(String... args) throws Exception {
		main(new RunAASOperationCommand(), args);
	}
}
