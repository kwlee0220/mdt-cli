package mdt.cli;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Duration;

import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Picoclies;
import utils.Throwables;
import utils.func.FOption;
import utils.func.Nullables;
import utils.io.FileUtils;

import mdt.client.HttpMDTManager;
import mdt.client.MDTClientConfig;
import mdt.model.MDTManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IExitCodeGenerator;
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
// 하위 명령의 '-V/--version'도 루트 명령과 같은 버전을 출력하도록 버전 제공자를 지정한다.
// (picocli는 상위 클래스의 @Command 속성을 하위 클래스에 상속한다.)
@Command(versionProvider = MDTVersionProvider.class)
public abstract class AbstractMDTCommand implements Runnable, IExitCodeGenerator, LoggerSettable {
	private static final org.slf4j.Logger s_logger = LoggerFactory.getLogger(AbstractMDTCommand.class);
	private org.slf4j.Logger m_logger;
	/** 명령의 종료 코드. 실행 중 실패를 기록하면 0이 아닌 값이 된다(작업 스레드에서도 기록할 수 있다). */
	private volatile int m_exitCode = 0;
	
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
			getLogger().debug("from option '--client_conf': {}", m_clientConfigFile.getAbsolutePath());
			return FOption.of(m_clientConfigFile);
		}
		
		// 그렇지 않은 경우는 설정 정보를 사용하거나 환경 변수를 활용하여 MDT Manager에 접속한다.
		File clientConfigFile = FileUtils.path(MDTCommandsMain.getClientHomeDir(), CLIENT_CONFIG_FILE);
		if ( clientConfigFile.canRead() ) {
			getLogger().debug("from environment-var({}): {}", "MDT_CLIENT_HOME", clientConfigFile.getAbsolutePath());
			return FOption.of(clientConfigFile);
		}
		
		return FOption.empty();
	}
	
	public FOption<MDTClientConfig> getClientConfig() throws IOException {
		return getClientConfigFile()
					.mapOrThrow(file -> {
						getLogger().debug("loading client config from file: {}", file.getAbsolutePath());
                        return MDTClientConfig.load(file);
					})
					.or(() -> {
						// client config file이 존재하지 않는 경우에는 환경변수 MDT_URL에 기록된
						// endpoint 정보를 사용하여 접속을 시도한다.
						return FOption.ofNullable(System.getenv("MDT_URL"))
										.map(url -> {
											getLogger().debug("loading client config from environment variable 'MDT_URL': {}", url);
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
		return Nullables.orElse(m_logger, s_logger);
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
	 * 명령의 종료 코드를 반환한다.
	 * <p>
	 * picocli의 {@link CommandLine#execute(String...)}와 {@link #main(AbstractMDTCommand, String...)}가 이 값을
	 * 프로세스 종료 코드로 사용한다. 실패가 기록되지 않았으면 {@code 0}이다.
	 *
	 * @return	종료 코드.
	 */
	@Override
	public int getExitCode() {
		return m_exitCode;
	}

	/**
	 * 명령 실행 중 실패가 있었음을 기록한다.
	 * <p>
	 * 예외를 던지지 않고 오류 메시지만 출력한 뒤 계속 진행하는 명령(예: 여러 인스턴스 중 일부만 실패)이
	 * 호출한다. 이후 {@link #getExitCode()}는 {@link #EXIT_CODE_FAILURE}를 반환한다.
	 */
	protected void markFailed() {
		m_exitCode = EXIT_CODE_FAILURE;
	}

	/** 실행 중 실패가 기록된 명령의 종료 코드. */
	protected static final int EXIT_CODE_FAILURE = 1;

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
	 * 표준 출력({@link System#out})에 쓰는 {@link PrintWriter}를 만든다.
	 * <p>
	 * 반환된 writer를 닫으면 남은 내용을 flush만 하고 {@link System#out}은 닫지 않는다. 따라서
	 * try-with-resources로 써도 이후의 표준 출력(로그의 콘솔 출력 등)이 사라지지 않는다.
	 *
	 * @return	표준 출력용 {@link PrintWriter}.
	 */
	protected static PrintWriter newStdoutWriter() {
		OutputStream closeShield = new FilterOutputStream(System.out) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				flush();
			}
		};
		return new PrintWriter(closeShield, true);
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
	 * 예외 없이 끝났더라도 명령이 실패를 기록했으면({@link #markFailed()}) {@link #getExitCode()}로 종료한다.
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
				if ( cmd.getExitCode() != 0 ) {
					System.exit(cmd.getExitCode());
				}
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
