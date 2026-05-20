package mdt.cli;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Picoclies;
import utils.Throwables;
import utils.func.FOption;
import utils.func.Optionals;
import utils.io.FileUtils;

import mdt.client.HttpMDTManager;
import mdt.client.MDTClientConfig;
import mdt.model.MDTManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;


/**
 * picocli 기반 MDT CLI 명령의 공통 기반 추상 클래스.
 * <p>
 * 공통 옵션({@code --client_conf}, {@code --loglevel})을 정의하고, {@link MDTManager}
 * 접속과 로그 레벨 적용 등 모든 명령에 공통으로 필요한 부트스트랩 로직을 제공한다. 서브클래스는
 * {@link #run(MDTManager)}을 구현하여 실제 명령 로직을 정의한다.
 * <p>
 * 명령 진입점에서는 {@link #main(AbstractMDTCommand, String...)}을 호출하여 picocli 파싱과
 * 실행을 한 번에 처리할 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractMDTCommand implements Runnable, LoggerSettable {
	private static final org.slf4j.Logger s_logger = LoggerFactory.getLogger(AbstractMDTCommand.class);
	private org.slf4j.Logger m_logger;
	
	private static final String CLIENT_CONFIG_FILE = "mdt_client_config.yaml";

	/**
	 * 사용자가 명시적으로 지정한 MDTManager 클라이언트 설정 파일 경로.
	 * <p>
	 * 지정되지 않은 경우 기본 설정 또는 환경 변수를 통해 접속한다.
	 */
	@Option(names={"--client_conf"}, paramLabel="path", required=false,
			description={"MDTManager configuration file path"})
	private File m_clientConfigFile;

	/**
	 * 서브클래스가 구현해야 하는 명령 본체.
	 * <p>
	 * {@link #run()}이 MDTManager 접속을 수행한 뒤 이 메서드를 호출하므로, 구현체는 비즈니스 로직에만
	 * 집중하면 된다.
	 *
	 * @param mdt 접속이 완료된 MDTManager 인스턴스.
	 * @throws Exception 명령 실행 중 발생한 임의의 예외.
	 */
	abstract protected void run(MDTManager mdt) throws Exception;

	/**
	 * 실제 서브클래스 타입을 이름으로 갖는 기본 로거로 초기화하는 생성자.
	 */
	public AbstractMDTCommand() {
		setLogger(s_logger);
	}

	/**
	 * 로거의 출력 레벨. {@code null}이면 명시적으로 변경하지 않는다.
	 */
	@Option(names={"--loglevel"}, paramLabel="logger-level",
					description={"Logger level: debug, info, warn, or error"})
	private Level m_logLevel = null;
	
	public FOption<File> getClientConfigFile() {
		if ( m_clientConfigFile != null ) {
			getLogger().debug("from option '--client_conf %s", m_clientConfigFile.getAbsolutePath());
			return FOption.of(m_clientConfigFile);
		}
		
		// 그렇지 않은 경우는 설정 정보를 사용하거나 환경 변수를 활용하여 MDT Manager에 접속한다.
		File clientConfigFile = FileUtils.path(MDTCommandsMain.getClientHomeDir(), CLIENT_CONFIG_FILE);
		if ( clientConfigFile.canRead() ) {
			getLogger().debug("from environment-var(%s) %s", "MDT_CLIENT_HOME", clientConfigFile.getAbsolutePath());
			return FOption.of(clientConfigFile);
		}
		
		return FOption.empty();
	}
	
	public FOption<MDTClientConfig> getClientConfig() throws IOException {
		return getClientConfigFile()
					.mapOrThrow(file -> {
						getLogger().debug("loading client config from file: %s", file.getAbsolutePath());
                        return MDTClientConfig.load(file);
					})
					.orElse(() -> {
						// client config file이 존재하지 않는 경우에는 환경변수 MDT_URL에 기록된
						// endpoint 정보를 사용하여 접속을 시도한다.
						return FOption.ofNullable(System.getenv("MDT_URL"))
										.map(url -> {
											getLogger().debug("loading client config from environment variable 'MDT_URL': %s", url);
											return MDTClientConfig.of(url);
										});
					});
	}

	/**
	 * 이 명령이 사용 중인 로거를 반환한다.
	 *
	 * @return 현재 로거.
	 */
	@Override
	public org.slf4j.Logger getLogger() {
		return Optionals.getOrElse(m_logger, s_logger);
	}

	/**
	 * 이 명령이 사용할 로거를 설정한다.
	 * <p>
	 * 인자가 {@code null}이면 실제 서브클래스 타입을 이름으로 갖는 기본 로거로 대체된다.
	 *
	 * @param logger 사용할 로거. {@code null}이면 기본값으로 대체된다.
	 */
	@Override
	public void setLogger(org.slf4j.Logger logger) {
		m_logger = logger;
	}

	/**
	 * 명령 실행 진입점.
	 * <p>
	 * 다음 절차를 수행한다.
	 * <ol>
	 *   <li>{@code --loglevel}이 지정된 경우 {@code "mdt"} 루트 로거에 해당 레벨을 적용한다.</li>
	 *   <li>설정 파일 또는 환경 변수를 통해 {@link HttpMDTManager}에 접속한다.</li>
	 *   <li>접속된 매니저를 인자로 서브클래스의 {@link #run(MDTManager)}을 호출한다.</li>
	 * </ol>
	 * 어떤 단계에서든 예외가 발생하면 {@link RuntimeException}으로 감싸 호출자에게 전달한다.
	 * 종료 코드 결정은 {@link #main(AbstractMDTCommand, String...)} 등 상위 호출자가 담당한다.
	 *
	 * @throws RuntimeException 명령 실행 중 발생한 모든 예외를 감싼 형태.
	 */
	@Override
	public void run() {
		if ( m_logLevel != null ) {
			Logger root = (Logger)LoggerFactory.getLogger("mdt");
			root.setLevel(m_logLevel);
		}

		try {
			HttpMDTManager mdt = connectMDTManager();
			run(mdt);
		}
		catch ( Exception e ) {
			throw Throwables.toRuntimeException(e);
		}
	}

	/**
	 * {@code --client_conf}로 지정된 설정 파일이 있으면 이를 사용하고, 그렇지 않으면 기본 설정/환경
	 * 변수를 사용하여 {@link HttpMDTManager}에 접속한다.
	 *
	 * @return 접속이 완료된 {@link HttpMDTManager}.
	 * @throws Exception 설정 로드 또는 접속에 실패한 경우.
	 */
	private HttpMDTManager connectMDTManager() throws Exception {
		HttpMDTManager mdt;

		// 사용자가 명시적으로 client 설정 정보를 지정한 경우에는 이를 통해 MDT Manager에 접속한다.
		if ( m_clientConfigFile != null ) {
			MDTClientConfig config = MDTClientConfig.load(m_clientConfigFile);
			mdt = HttpMDTManager.connect(config);
		}
		// 그렇지 않은 경우는 설정 정보를 사용하거나 환경 변수를 활용하여 MDT Manager에 접속한다.
		else {
			mdt = HttpMDTManager.connectWithDefault();
		}
		getLogger().debug("connecting to MDTInstanceManager {}", mdt.getEndpoint());

		return mdt;
	}

	/**
	 * CLI 진입점 공통 메인 함수.
	 * <p>
	 * picocli {@link CommandLine}을 구성하고 인자를 파싱한 뒤, 도움말 요청이면 사용법을 표준 출력으로
	 * 내보내고 그렇지 않으면 {@code cmd}의 {@link #run()}을 호출한다. 오류 처리는 다음과 같이 구분된다.
	 * <ul>
	 *   <li>{@link CommandLine.ParameterException}(파싱/검증 오류): 에러 메시지와 사용법을
	 *       표준 에러로 출력한다.</li>
	 *   <li>그 외 예외(명령 실행 중 오류): 원인 stacktrace를 표준 에러로 출력한다. 사용법은
	 *       출력하지 않는다.</li>
	 * </ul>
	 * 두 경우 모두 종료 코드 {@code -1}로 프로세스를 종료한다.
	 * <p>
	 * 다음 picocli 기본 동작이 적용된다.
	 * <ul>
	 *   <li>enum 값 대소문자 구분 없음</li>
	 *   <li>옵션/서브커맨드 약어 사용 허용</li>
	 *   <li>{@link Duration} 및 {@link Level} 타입 변환기 등록</li>
	 *   <li>사용법 너비 110 컬럼</li>
	 * </ul>
	 *
	 * @param cmd  실행할 명령 인스턴스.
	 * @param args 커맨드라인 인자.
	 * @throws Exception picocli 구성 중 발생한 예외.
	 */
	protected static void main(AbstractMDTCommand cmd, String... args) throws Exception {
		CommandLine commandLine = new CommandLine(cmd)
										.setCaseInsensitiveEnumValuesAllowed(true)
										.setAbbreviatedOptionsAllowed(true)
										.setAbbreviatedSubcommandsAllowed(true)
										.registerConverter(Duration.class, new Picoclies.DurationConverter())
										.registerConverter(Level.class, new Picoclies.LogLevelConverter())
										.setUsageHelpWidth(110);
		try {
			commandLine.parseArgs(args);

			if ( commandLine.isUsageHelpRequested() ) {
				commandLine.usage(System.out, Ansi.OFF);
			}
			else {
				cmd.run();
			}
		}
		catch ( CommandLine.ParameterException e ) {
			System.err.println(e.getMessage());
			commandLine.usage(System.err, Ansi.OFF);
			System.exit(-1);
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			cause.printStackTrace(System.err);
			System.exit(-1);
		}
	}
}
