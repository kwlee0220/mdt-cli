package mdt.task.skku;

import java.time.Duration;


import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.DefaultSubmodelReference;

import picocli.CommandLine.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@picocli.CommandLine.Command(name = "skku", description = "SKKU Simulation")
public class SKKUSimulationTaskCommand extends AbstractMDTCommand {
	@Option(names={"--simulation"}, paramLabel="reference",
			description="the reference to Simulation Submodel")
	private String m_simSubmodelRefString;
	
	@Option(names={"--timeout"}, paramLabel="duration",
			description="Invocation timeout (e.g. \"30s\", \"1m\", default: none)")
	protected Duration m_timeout = null;

	@Option(names={"--poll"}, paramLabel="duration",
			description="Status polling interval (e.g. \"1s\", \"500ms\")")
	private Duration m_pollInterval = SKKUSimulationTask.DEFAULT_POLL_INTERVAL;
	/** 이전 옵션 이름 {@code --pollInterval}(도움말에는 표시하지 않는다). */
	@Option(names={"--pollInterval"}, hidden=true)
	private void setPollIntervalAlias(Duration interval) {
		m_pollInterval = interval;
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		SKKUSimulationTask task = new SKKUSimulationTask();
		
		DefaultSubmodelReference simRef = DefaultSubmodelReference.parseStringExpr(m_simSubmodelRefString);
		simRef.activate(manager);
		
		task.setSimulationSubmodelReference(simRef);
		task.setPollInterval(m_pollInterval);
		task.setTimeout(m_timeout);
		
		task.run(manager);
	}

	public static final void main(String... args) throws Exception {
		main(new SKKUSimulationTaskCommand(), args);
	}
}