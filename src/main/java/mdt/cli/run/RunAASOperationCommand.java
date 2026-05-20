package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;

import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import utils.Throwables;
import utils.UnitUtils;
import utils.func.Unchecked;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.client.operation.AASOperationClient;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerAware;
import mdt.model.sm.ref.DefaultElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.value.ElementValue;
import mdt.task.TaskException;
import mdt.task.builtin.MultiVariablesCommand;
import mdt.workflow.model.ArgumentSpec.ReferenceArgumentSpec;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "operation",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Run an AAS Operation."
)
public class RunAASOperationCommand extends MultiVariablesCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(RunAASOperationCommand.class);
	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);
	
	@Parameters(index="0", paramLabel="id", description="MDTInstance id.")
	private String m_instanceId;
	
	@Parameters(index="1", paramLabel="submodel-idShort", description="Target AI/Simulation submodel idShort")
	private String m_submodelIdShort;
	
	@Parameters(index="2", paramLabel="path", defaultValue="*", description="Target SubmodelElement idShortPath")
	private String m_path;
	
	@Option(names={"--timeout"}, paramLabel="duration", description="Invocation timeout (e.g. \"30s\", \"1m\")")
	public void setTimeout(String timeout) {
		m_timeout = UnitUtils.parseDuration(timeout);
	}
	protected Duration m_timeout = null;

	@Option(names={"--poll"}, paramLabel="duration", description="Status polling interval (e.g. \"1s\", \"500ms\"")
	public void setPollInterval(String interval) {
		m_pollInterval = UnitUtils.parseDuration(interval);
	}
	private Duration m_pollInterval = DEFAULT_POLL_INTERVAL;

	@Option(names={"--showResult"}, description="show output/inoutput operation variables")
	private boolean m_showResult = false;
	
	private final LinkedHashMap<String, SubmodelElement> m_inputArguments = Maps.newLinkedHashMap();
	private final LinkedHashMap<String, SubmodelElement> m_outputArguments = Maps.newLinkedHashMap();
	private final LinkedHashMap<String, SubmodelElement> m_inoutputArguments = Maps.newLinkedHashMap();
	
	public RunAASOperationCommand() {
		setLogger(s_logger);
	}
	
	public void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		String opRefStr = String.format("%s:%s:%s", m_instanceId, m_submodelIdShort, m_path);
		DefaultElementReference opRef = (DefaultElementReference)ElementReferences.parseExpr(opRefStr);
		opRef.activate(manager);
		
		loadArgumentFromAASOperation(opRef);

		// Command line 인자를로부터 전달된 input/output/inoutput 변수 값을 수집한다.
		TaskArgumentsDescriptor tvsDesc = loadTaskArgumentsFromCommandLine();
		
		AASOperationClient opSvc = new AASOperationClient(opRef.getSubmodelService(), m_path, m_pollInterval);
		
		// Command line의 input 변수 값을 input OperationVariable에 설정한다.
		KeyValueFStream.from(tvsDesc.getInputs())
					    .forEachOrThrow((argId, argSpec) -> {
							try {
								argSpec = MDTInstanceManagerAware.activate(argSpec, manager);
								opSvc.setInputVariableValue(argId, argSpec.readValue());
							}
							catch ( Exception e ) {
								Throwable cause = Throwables.unwrapThrowable(e);
								String msg = String.format("Failed to set input argument: id=%s, cause=%s", argId, cause);
								throw new TaskException(msg, cause);
							}
					    });
		
		// Command line의 inoutput 변수 값을 inoutput OperationVariable에 설정한다.
		KeyValueFStream.from(tvsDesc.getInoutputs())
			    .forEachOrThrow((argId, argSpec) -> {
					try {
						argSpec = MDTInstanceManagerAware.activate(argSpec, manager);
						opSvc.setInoutputVariableValue(argId, argSpec.readValue());
					}
					catch ( Exception e ) {
						Throwable cause = Throwables.unwrapThrowable(e);
						String msg = String.format("Failed to set inoutput argument: id=%s, cause=%s",
													argId, cause);
						throw new TaskException(msg, cause);
					}
			    });
		
		FStream.from(tvsDesc.getOutputs().values())
				.forEach(arg -> MDTInstanceManagerAware.activate(arg, manager));
		
		opSvc.setTimeout(m_timeout);
		opSvc.run();
		
		KeyValueFStream.from(tvsDesc.getOutputs())
						.match(opSvc.getOutputVariableValues())
						.forEachOrThrow((k, match) -> {
							ReferenceArgumentSpec outVar = (ReferenceArgumentSpec)match._1;
							try {
								ElementValue val = match._2;
								Unchecked.acceptOrThrowSneakily(val, outVar::updateValue);
							}
							catch ( Exception e ) {
								Throwable cause = Throwables.unwrapThrowable(e);
								String msg = String.format("Failed to set output variable: id=%s, cause=%s",
															k, cause);
								throw new TaskException(msg, cause);
							}
						});
		KeyValueFStream.from(tvsDesc.getInoutputs())
						.match(opSvc.getInoutputVariableValues())
						.forEachOrThrow((k, match) -> {
							ReferenceArgumentSpec outVar = (ReferenceArgumentSpec)match._1;
							try {
								ElementValue val = match._2;
								Unchecked.acceptOrThrowSneakily(val, outVar::updateValue);
							}
							catch ( Exception e ) {
								Throwable cause = Throwables.unwrapThrowable(e);
								String msg = String.format("Failed to set inoutput variable: id=%s, cause=%s",
															k, cause);
								throw new TaskException(msg, cause);
							}
						});
		if ( m_showResult ) {
			opSvc.getOutputVariableValues().forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
			opSvc.getInoutputVariableValues().forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
		}
	}
	
	private void loadArgumentFromAASOperation(DefaultElementReference opRef) throws IOException {
		SubmodelElement sme = opRef.read();
		if ( !(sme instanceof Operation) ) {
			throw new IllegalArgumentException("Target SubmodelElement is not an Operation: " + sme);
		}
		Operation op = (Operation)sme;
		
		FStream.from(op.getInputVariables())
			    .forEach(opv -> {
			    	SubmodelElement arg = opv.getValue();
			    	m_inputArguments.put(arg.getIdShort(), arg);
			    });
		FStream.from(op.getOutputVariables())
			    .forEach(opv -> {
			    	SubmodelElement arg = opv.getValue();
			    	m_outputArguments.put(arg.getIdShort(), arg);
			    });
		FStream.from(op.getInoutputVariables())
			    .forEach(opv -> {
			    	SubmodelElement arg = opv.getValue();
			    	m_inoutputArguments.put(arg.getIdShort(), arg);
			    });
	}
	
	public static void main(String... args) throws Exception {
		main(new RunAASOperationCommand(), args);
	}
}
