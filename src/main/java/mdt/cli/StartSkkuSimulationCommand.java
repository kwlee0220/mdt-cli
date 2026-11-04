package mdt.cli;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Funcs;

import mdt.aas.SubmodelRegistry;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.client.operation.HttpSimulationClient;
import mdt.client.operation.OperationStatus;
import mdt.client.operation.OperationStatusResponse;
import mdt.client.resource.HttpSubmodelServiceClient;
import mdt.model.DescriptorUtils;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.model.sm.SubmodelUtils;
import mdt.model.sm.simulation.Simulation;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "run",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Start a simulation."
)
public class StartSkkuSimulationCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StartSkkuSimulationCommand.class);
	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);
	
	@Parameters(index="0", paramLabel="instance-id|submodel-id", description="target MDTInstance id or Simulation Submodel id")
	private String m_targetId;

	@Option(names={"--use-endpoint", "-e"},
			description="Invoke simulation with Simulation submodel service endpoint")
	private boolean m_useEndpoint;
	/** 이전 옵션 이름 {@code --use_endpoint}(도움말에는 표시하지 않는다). */
	@Option(names={"--use_endpoint"}, hidden=true)
	private void setUseEndpointAlias(boolean useEndpoint) {
		m_useEndpoint = useEndpoint;
	}

	@Option(names={"--nowait"}, paramLabel="duration",
			description="Do not wait until the instance gets to running")
	private boolean m_nowait = false;

	@Option(names={"--poll"}, paramLabel="duration",
			description="Status polling interval (e.g. \"5s\", \"500ms\", default: 3s)")
	private Duration m_pollInterval = DEFAULT_POLL_INTERVAL;

	@Option(names={"--timeout", "-t"}, paramLabel="duration",
			description="Simulation timeout (e.g. \"30s\", \"5m\", default: none)")
	private Duration m_timeout = null;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new StartSkkuSimulationCommand(), args);
	}

	public StartSkkuSimulationCommand() {
		setLogger(s_logger);
	}
		
	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager client = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		SubmodelRegistry registry = mdt.getSubmodelRegistry();
		
		HttpMDTInstanceClient inst;
		SubmodelDescriptor simulationSubmodelDesc;
		try {
			simulationSubmodelDesc = registry.getSubmodelDescriptorById(m_targetId);
			if ( !Simulation.SEMANTIC_ID_REFERENCE.equals(simulationSubmodelDesc.getSemanticId()) ) {
				err().printf("The target Submodel(%s) is not a Simulation%n", m_targetId);
				markFailed();
				return;
			}
		}
		catch ( ResourceNotFoundException expected ) {
			// 인자로 주어진 식별자에 해당하는 Submodel이 존재하지 않는 경우에는
			// 식별자에 해당하는 MDTInstance를 검색하여 해당 MDTInstance에 포함된
			// Simulation Submodel의 갯수가 1개 인 경우에는 이것을 사용한다.
			inst = client.getInstance(m_targetId);
			List<SubmodelDescriptor> simulations 
					= Funcs.filter(inst.getAASSubmodelDescriptorAll(),
									desc -> Simulation.SEMANTIC_ID_REFERENCE.equals(desc.getSemanticId()));
			if ( simulations.size() == 0 ) {
				err().printf("Invalid Simulation Submodel id: %s%n", m_targetId);
				markFailed();
				return;
			}
			simulationSubmodelDesc = simulations.get(0);
		}
		
		String simulationSubmodelEndpoint = DescriptorUtils.getEndpointString(simulationSubmodelDesc.getEndpoints());
		if ( simulationSubmodelEndpoint == null ) {
			err().printf("Target Simulation is not ready to run. Submodel id: %s%n",
								simulationSubmodelDesc.getId());
			markFailed();
			return;
		}
		HttpSubmodelServiceClient svc = HttpSubmodelServiceClient.newTrustAllSubmodelServiceClient(simulationSubmodelEndpoint);
		
		Submodel simulation = svc.getSubmodel();
		String endpoint = SubmodelUtils.getPropertyValueByPath(simulation,
																Simulation.IDSHORT_PATH_ENDPOINT,
																String.class);
		if ( endpoint == null || endpoint.trim().length() == 0 ) {
			err().printf("Simulator Endpoint is missing: submodel-id=%s%n", simulation.getId());
			markFailed();
			return;
		}
		
		HttpSimulationClient simClient = new HttpSimulationClient(client.getHttpClient(), endpoint);
		simClient.setLogger(getLogger());
		
		Instant started = Instant.now();
		OperationStatusResponse<Void> resp = (m_useEndpoint)
								? simClient.startSimulationWithEndpoint(simulationSubmodelEndpoint)
								: simClient.startSimulationWithSumodelId(simulationSubmodelDesc.getId());
		
		if ( m_nowait ) {
			String loc = resp.getOperationLocation();
			String handleStr = (loc != null) ? String.format(", url=%s/%s", endpoint, loc) : "";
			String msg = (resp.getMessage() != null) ? String.format(", message=%s", resp.getMessage()) : "";
			out().printf("Starting a simulation: id=%s%s, status=%s%s%n",
							m_targetId, handleStr, resp.getStatus(), msg);
			return;
		}
		
//		String opHandle = "ProcessOptimization";
		String opHandle = resp.getOperationLocation();
		while ( resp.getStatus() == OperationStatus.RUNNING ) {
			if ( m_verbose ) {
				out().print(".");
			}
			TimeUnit.MILLISECONDS.sleep(m_pollInterval.toMillis());
			
			resp = simClient.statusSimulation(opHandle);
			if ( m_timeout != null && resp.getStatus() == OperationStatus.RUNNING ) {
				if ( m_timeout.minus(Duration.between(started, Instant.now())).isNegative() ) {
					out().println();
					simClient.cancelSimulation(opHandle);
					
					// 시간 초과로 취소한 것이므로 실패로 끝낸다.
					err().printf("Simulation is cancelled by timeout: id=%s, cause=%s%n",
										m_targetId, resp.getMessage());
					markFailed();
					return;
				}
			}
		}
		out().println();
		
		switch ( resp.getStatus() ) {
			case COMPLETED:
				out().printf("Simulation completes: id=%s%n", m_targetId);
				break;
			case FAILED:
				out().printf("Simulation is failed: id=%s, cause=%s%n", m_targetId, resp.getMessage());
				markFailed();
				break;
			case CANCELLED:
				out().printf("Simulation is cancelled: id=%s, cause=%s%n", m_targetId, resp.getMessage());
				markFailed();
				break;
			default:
				throw new AssertionError();
		}
	}
}
