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
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

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
	@Spec private CommandSpec m_spec;
	
	@Parameters(index="0", paramLabel="id", description="target Simulation Submodel (or MDTInstance) id")
	private String m_targetId;
	
	@Parameters(index="1", paramLabel="opId", description="target Simulation operation id")
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
							    .orElse(() -> inst.getSubmodelServiceByIdShort("Simulation"))
							    .getOrThrow(() -> {
									return new CommandLine.ParameterException(getCommandLine(),
												String.format("no Simulation submodel found for id=%s", m_targetId));
							    });
		
		Submodel simModel = svc.getSubmodel();
		String endpoint = SubmodelUtils.getPropertyValueByPath(simModel,
																Simulation.IDSHORT_PATH_ENDPOINT,
																String.class);
		
		HttpSimulationClient simulation = new HttpSimulationClient(client.getHttpClient(), endpoint);
		
		OperationStatusResponse<Void> resp = simulation.cancelSimulation(m_opId);
		switch ( resp.getStatus() ) {
			case COMPLETED:
				System.out.println("Simulation completes");
				System.exit(0);
				break;
			case FAILED:
				System.out.printf("Simulation is failed: cause=%s%n", resp.getMessage());
				System.exit(-1);
				break;
			case CANCELLED:
				System.out.println("Simulation is cancelled");
				System.exit(-1);
				break;
			default:
				throw new AssertionError();
		}
	}

	/**
	 * 검증 메시지 발생용 {@link CommandLine}을 반환한다. picocli가 정상적으로 파싱한 경우
	 * 주입된 {@link #m_spec}에서 가져오고, 그렇지 않으면 새로 생성한 인스턴스를 사용한다.
	 *
	 * @return 이 명령에 연결된 {@link CommandLine}.
	 */
	private CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
}
