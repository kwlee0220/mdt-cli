package mdt.cli;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Throwables;
import utils.func.Nullables;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;


/**
 * 모든 MDT CLI 명령의 공통 기반 추상 클래스.
 * <p>
 * 로그 레벨 옵션({@code --loglevel}), 로거, 종료 코드, 표준 출력 writer, 단독 실행용 {@code main}을 제공한다.
 * MDTManager에 접속하지 않는 명령(예: {@code build}, {@code endpoints})은 이 클래스를 직접 상속해
 * {@link #execute()}를 구현하고, 접속이 필요한 명령은 {@link AbstractMDTCommand}를 상속한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
// 하위 명령의 '-V/--version'도 루트 명령과 같은 버전을 출력하도록 버전 제공자를 지정한다.
// (picocli는 상위 클래스의 @Command 속성을 하위 클래스에 상속한다.)
@Command(versionProvider = MDTVersionProvider.class)
public abstract class AbstractCommand implements Runnable, IExitCodeGenerator, LoggerSettable {
	private static final org.slf4j.Logger s_logger = LoggerFactory.getLogger(AbstractCommand.class);
	private org.slf4j.Logger m_logger;
	/** 명령의 종료 코드. 실행 중 실패를 기록하면 0이 아닌 값이 된다(작업 스레드에서도 기록할 수 있다). */
	private volatile int m_exitCode = 0;

	/** 실행 중 실패가 기록된 명령의 종료 코드. */
	protected static final int EXIT_CODE_FAILURE = 1;
	
	@Spec private CommandSpec m_spec;

	/**
	 * 로거의 출력 레벨. {@code null}이면 명시적으로 변경하지 않는다.
	 */
	@Option(names={"--loglevel"}, paramLabel="logger-level",
					description={"Logger level: debug, info, warn, or error"})
	private Level m_logLevel = null;

	/**
	 * 옵션·인자 조합을 검사한다. {@link #execute()}보다 먼저(MDTManager 접속 전에) 호출된다.
	 * <p>
	 * 기본 구현은 아무것도 하지 않는다. 잘못된 입력은 {@link #usageError(String)}로 만든 예외를 던진다.
	 *
	 * @throws Exception	입력이 잘못된 경우.
	 */
	protected void validate() throws Exception { }

	/**
	 * 서브클래스가 구현하는 명령 본체.
	 *
	 * @throws Exception 명령 실행 중 발생한 임의의 예외.
	 */
	abstract protected void execute() throws Exception;

	/**
	 * 기본 로거로 초기화하는 생성자.
	 */
	protected AbstractCommand() {
		setLogger(s_logger);
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
	 *
	 * @param logger 사용할 로거. {@code null}이면 기본 로거를 쓴다.
	 */
	@Override
	public void setLogger(org.slf4j.Logger logger) {
		m_logger = logger;
	}

	/**
	 * 명령의 종료 코드를 반환한다.
	 * <p>
	 * picocli의 {@link CommandLine#execute(String...)}와 {@link #main(AbstractCommand, String...)}가 이 값을
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

	/**
	 * 이 명령의 {@link CommandLine}을 반환한다.
	 *
	 * @return	picocli가 주입한 명령의 {@link CommandLine}. picocli 밖에서 만든 경우는 새로 만든 것.
	 */
	protected CommandLine getCommandLine() {
		return (m_spec != null) ? m_spec.commandLine() : new CommandLine(this);
	}
	
	/**
	 * 이 명령부터 부모 명령 쪽으로 올라가며 {@code getter}가 {@code null}이 아닌 값을 돌려주는 첫 값을 찾는다.
	 * <p>
	 * 예를 들어 {@code mdt get instance demo --client-conf X parameters}에서 {@code parameters} 명령은 자신에게
	 * 지정되지 않은 {@code --client-conf}를 부모 {@code instance} 명령에서 가져온다.
	 *
	 * @param <T>		값의 타입.
	 * @param getter	명령에서 값을 꺼내는 함수.
	 * @return	가장 가까운 명령의 값. 어디에도 없으면 {@code null}.
	 */
	protected <T> @Nullable T findNearest(Function<AbstractCommand, T> getter) {
		T value = getter.apply(this);
		for ( CommandSpec spec = (m_spec != null) ? m_spec.parent() : null;
				value == null && spec != null;
				spec = spec.parent() ) {
			if ( spec.userObject() instanceof AbstractCommand parent ) {
				value = getter.apply(parent);
			}
		}
		return value;
	}
	
	/**
	 * 사용자 입력(옵션·인자) 오류를 나타내는 예외를 만든다.
	 * <p>
	 * 이 예외를 던지면 picocli가 메시지와 사용법을 표준 에러로 출력하고 종료 코드 2로 끝낸다.
	 *
	 * @param message	오류 메시지.
	 * @return	사용법 오류 예외.
	 */
	protected CommandLine.ParameterException usageError(String message) {
		return new CommandLine.ParameterException(getCommandLine(), message);
	}

	/**
	 * 명령 실행 진입점.
	 * <p>
	 * {@code --loglevel}이 지정된 경우 {@code "mdt"} 로거에 해당 레벨을 적용한 뒤 {@link #execute()}를 호출한다.
	 * 예외는 {@link RuntimeException}으로 감싸 전달하며, 출력과 종료 코드는 {@link CommandExceptionHandler}가
	 * 정한다.
	 *
	 * @throws RuntimeException 명령 실행 중 발생한 예외를 감싼 형태.
	 */
	@Override
	public void run() {
		// 하위 명령에 지정하지 않았으면 가장 가까운 부모 명령의 '--loglevel'을 쓴다.
		Level logLevel = findNearest(cmd -> cmd.m_logLevel);
		if ( logLevel != null ) {
			Logger root = (Logger)LoggerFactory.getLogger("mdt");
			root.setLevel(logLevel);
		}

		try {
			// 잘못된 입력이 접속 설정 오류 등에 가려지지 않도록 접속·서비스 호출보다 먼저 검사한다.
			validate();
			execute();
		}
		catch ( Exception e ) {
			throw Throwables.toRuntimeException(e);
		}
		finally {
			// print()만 한 출력이 프로세스 종료로 사라지지 않도록 비운다.
			out().flush();
			err().flush();
		}
	}
	
	/**
	 * 명령 결과를 쓰는 출력 writer(picocli의 {@link CommandLine#getOut()})를 반환한다.
	 *
	 * @return	출력 writer.
	 */
	protected PrintWriter out() {
		return getCommandLine().getOut();
	}
	
	/**
	 * 오류·진행 메시지를 쓰는 writer(picocli의 {@link CommandLine#getErr()})를 반환한다.
	 *
	 * @return	오류 writer.
	 */
	protected PrintWriter err() {
		return getCommandLine().getErr();
	}

	/**
	 * 출력 writer({@link #out()})를 감싼 {@link PrintWriter}를 만든다.
	 * <p>
	 * 반환된 writer를 닫으면 남은 내용을 flush만 하고 출력 writer는 닫지 않는다. 따라서 try-with-resources로 써도
	 * 이후의 출력이 사라지지 않는다.
	 *
	 * @return	닫아도 출력 writer를 닫지 않는 {@link PrintWriter}.
	 */
	protected PrintWriter newOutWriter() {
		return closeShielded(out());
	}
	
	/**
	 * 주어진 writer를 감싸, 닫으면 flush만 하는 {@link PrintWriter}를 만든다.
	 *
	 * @param writer	감쌀 writer.
	 * @return	닫아도 {@code writer}를 닫지 않는 {@link PrintWriter}.
	 */
	static PrintWriter closeShielded(Writer writer) {
		Writer shield = new FilterWriter(writer) {
			@Override
			public void close() throws IOException {
				flush();
			}
		};
		return new PrintWriter(shield, true);
	}

	/**
	 * 명령을 단독으로 실행하는 공통 메인 함수.
	 * <p>
	 * {@link MDTCommandsMain#configureLogging()}으로 로그 설정을 읽은 뒤, {@link MDTCommandsMain#newCommandLine(Object)}로
	 * {@code mdt} 진입점과 같은 설정의 {@link CommandLine}을 만들어 실행하고, 그 종료 코드로 프로세스를 종료한다.
	 * 인자 오류는 메시지와 사용법을 표준 에러로 출력하고(종료 코드 2), 실행 중 예외는 {@link CommandExceptionHandler}가
	 * 처리한다(종료 코드 1). 예외 없이 끝났더라도 명령이 실패를 기록했으면({@link #markFailed()})
	 * {@link #getExitCode()}로 종료한다.
	 *
	 * @param cmd  실행할 명령 인스턴스.
	 * @param args 커맨드라인 인자.
	 * @throws Exception	로그 설정을 읽지 못한 경우.
	 */
	protected static void main(AbstractCommand cmd, String... args) throws Exception {
		MDTCommandsMain.configureLogging();
		System.exit(MDTCommandsMain.newCommandLine(cmd).execute(args));
	}
}
