package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;

import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import utils.UnitUtils;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

import mdt.client.operation.AASOperationClient;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerAware;
import mdt.model.sm.ref.DefaultElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;
import mdt.task.builtin.MultiVariablesCommand;
import mdt.workflow.model.ArgumentSpec;
import mdt.workflow.model.ArgumentSpec.LiteralArgumentSpec;
import mdt.workflow.model.ArgumentSpec.ReferenceArgumentSpec;


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
						.match(m_inputArguments)
						.forEachOrThrow((argId, match) -> {
							ArgumentSpec argSpec = MDTInstanceManagerAware.activate(match._1, manager);
							SubmodelElement var = readArgument(match._2, argSpec);
							opSvc.setInputVariable(argId, var);
						});
		
		// Command line의 inoutput 변수 값을 inoutput OperationVariable에 설정한다.
		KeyValueFStream.from(tvsDesc.getInoutputs())
						.match(m_inoutputArguments)
						.forEachOrThrow((argId, match) -> {
							ArgumentSpec argSpec = MDTInstanceManagerAware.activate(match._1, manager);
							SubmodelElement var = readArgument(match._2, argSpec);
							opSvc.setInputVariable(argId, var);
						});
		
		FStream.from(tvsDesc.getOutputs().values())
				.forEach(arg -> MDTInstanceManagerAware.activate(arg, manager));
		
		opSvc.setTimeout(m_timeout);
		opSvc.run();

		// 출력 변수의 값을 수집된 출력 값으로 갱신한다.
		KeyValueFStream.from(tvsDesc.getOutputs())
						.match(opSvc.getOutputVariables())
						.forEach((argId, match) -> {
							ReferenceArgumentSpec outArgSpec = match._1;
							try {
								SubmodelElement outVal = match._2.getValue();
								outArgSpec.updateValue(ElementValues.getValue(outVal));
							}
							catch ( IOException e ) {
								getLogger().warn("Failed to update output argument: id={}, cause={}", argId, e);
							}
						});
		
		if ( m_showResult ) {
			KeyValueFStream.from(opSvc.getOutputVariables())
							.mapValue(var -> ElementValues.getValue(var.getValue()))
							.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
			KeyValueFStream.from(opSvc.getInoutputVariables())
							.mapValue(var -> ElementValues.getValue(var.getValue()))
							.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
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
	
	private SubmodelElement readArgument(SubmodelElement proto, ArgumentSpec argSpec) throws Exception {
		if ( argSpec instanceof ReferenceArgumentSpec refArgSpec ) {
			// 'Value' 값만 읽어오는 경우 (특히 SMC/SML 의 경우), 읽어온 값을 모두 반영할 수 없기
			// 때문에 SubmodelElement 전체를 읽어온다.
			// 특히 Timeseries SMC의 경우에는 가변 길이의 값을 지원할 수 없게 됨.
			return refArgSpec.read();
		}
		else if ( argSpec instanceof LiteralArgumentSpec litArgSpec ) {
			ElementValue argv = litArgSpec.readValue();
			ElementValues.update(proto, argv);
			return proto;
		}
		else {
			throw new IllegalArgumentException("Unsupported ArgumentSpec: " + argSpec.getClass().getName());
		}
	}
}
