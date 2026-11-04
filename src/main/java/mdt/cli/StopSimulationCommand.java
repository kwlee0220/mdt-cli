package mdt.cli;

import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.client.instance.HttpMDTInstanceManager;
import mdt.client.operation.HttpSimulationClient;
import mdt.client.operation.OperationStatusResponse;
import mdt.model.MDTManager;
import mdt.model.SubmodelService;
import mdt.model.instance.MDTInstance;
import mdt.model.sm.SubmodelUtils;
import mdt.model.sm.simulation.Simulation;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "simulation",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Stop a simulation."
)
public class StopSimulationCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(StopSimulationCommand.class);

	/**
	 * picocli가 주입하는 이 명령의 {@link CommandSpec}. 검증 실패 시 {@link CommandLine}을
	 * 얻어 {@link CommandLine.ParameterException}을 발생시키는 데 사용한다.
	 * 직접 인스턴스화 등 picocli 파싱을 거치지 않은 경우 {@code null}일 수 있다.
	 */
	
	@Parameters(index="0", paramLabel="instance-id|submodel-id", description="target MDTInstance id or Simulation Submodel id")
	private String m_targetId;
	
	@Parameters(index="1", paramLabel="operation-id", description="target Simulation operation id")
	private String m_opId;

	public static final void main(String... args) throws Exception {
		main(new StopSimulationCommand(), args);
	}

	public StopSimulationCommand() {
		setLogger(s_logger);
	}
		
	@Override
	public void run(MDTManager manager) throws Exception {
		HttpMDTInstanceManager client = (HttpMDTInstanceManager)manager.getInstanceManager();

		MDTInstance inst = client.getInstanceBySubmodelId(m_targetId);
		
		SubmodelService svc = inst.getSubmodelServiceById(m_targetId)
							    .or(() -> inst.getSubmodelServiceByIdShort("Simulation"))
							    .getOrThrow(() -> {
									return usageError(String.format("no Simulation submodel found for id=%s", m_targetId));
							    });
		
		Submodel simModel = svc.getSubmodel();
		String endpoint = SubmodelUtils.getPropertyValueByPath(simModel,
																Simulation.IDSHORT_PATH_ENDPOINT,
																String.class);
		
		HttpSimulationClient simulation = new HttpSimulationClient(client.getHttpClient(), endpoint);
		
		OperationStatusResponse<Void> resp = simulation.cancelSimulation(m_opId);
		// 실패는 markFailed()로 기록하며, picocli가 getExitCode()를 종료 코드로 쓴다.
		switch ( resp.getStatus() ) {
			case CANCELLED:
				out().println("Simulation is cancelled");
				break;
			case COMPLETED:
				// 취소 요청 전에 이미 끝난 경우.
				out().println("Simulation completes");
				break;
			case RUNNING:
				// 취소 요청은 받아들여졌지만 아직 종료되는 중인 경우.
				out().println("Simulation cancellation is requested, but it is still running");
				break;
			case FAILED:
				out().printf("Simulation is failed: cause=%s%n", resp.getMessage());
				markFailed();
				break;
		}
	}

}
