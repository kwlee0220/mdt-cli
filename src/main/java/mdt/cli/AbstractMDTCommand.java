package mdt.cli;

import java.io.File;
import java.util.function.Function;

import mdt.client.HttpMDTManager;
import mdt.client.MDTClientConfig;
import mdt.model.MDTManager;

import picocli.CommandLine.Mixin;


/**
 * MDTManager에 접속하는 MDT CLI 명령의 공통 기반 추상 클래스.
 * <p>
 * {@link AbstractCommand}의 공통 기능에 클라이언트 설정 옵션({@code --client-conf}, {@link ClientConfigOptions})과
 * MDTManager 접속을 더한다. 서브클래스는 {@link #run(MDTManager)}을 구현하여 실제 명령 로직을 정의한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractMDTCommand extends AbstractCommand {
	@Mixin private ClientConfigOptions m_clientConfig;
	
	/**
	 * 클라이언트 설정으로 MDTManager에 접속하는 함수. 기본은 {@link HttpMDTManager#connect(MDTClientConfig)}이며,
	 * 같은 패키지의 테스트가 가짜 매니저를 돌려주는 함수로 바꿔 쓴다.
	 */
	static volatile Function<MDTClientConfig, MDTManager> s_connector = HttpMDTManager::connect;

	/**
	 * 서브클래스가 구현해야 하는 명령 본체.
	 * <p>
	 * {@link #execute()}가 MDTManager 접속을 수행한 뒤 이 메서드를 호출하므로, 구현체는 비즈니스 로직에만
	 * 집중하면 된다.
	 *
	 * @param mdt 접속이 완료된 MDTManager 인스턴스.
	 * @throws Exception 명령 실행 중 발생한 임의의 예외.
	 */
	abstract protected void run(MDTManager mdt) throws Exception;

	/**
	 * 클라이언트 설정으로 {@link HttpMDTManager}에 접속한 뒤 {@link #run(MDTManager)}을 호출한다.
	 *
	 * @throws Exception	설정을 찾거나 읽지 못했거나, 명령 실행 중 예외가 발생한 경우.
	 */
	@Override
	protected void execute() throws Exception {
		// 하위 명령에 지정하지 않았으면 가장 가까운 부모 명령의 '--client-conf'를 쓴다.
		File configFile = findNearest(cmd -> (cmd instanceof AbstractMDTCommand mdtCmd)
												? mdtCmd.m_clientConfig.getConfigFile() : null);
		MDTClientConfig config = ClientConfigOptions.loadClientConfig(configFile)
								.getOrThrow(() -> new IllegalArgumentException(
										"MDTManager endpoint is not configured: use --client-conf, "
										+ "$MDT_CLIENT_HOME/" + MDTClientConfig.CONFIG_FILE_NAME + " or MDT_URL"));
		getLogger().debug("connecting to MDTManager {}", config.getMdtUrl());
		MDTManager mdt = s_connector.apply(config);

		run(mdt);
	}
}
