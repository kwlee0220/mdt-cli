package mdt.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import utils.MovingAverage;
import utils.UnitUtils;
import utils.async.PeriodicLoopExecution;
import utils.func.FOption;


/**
 * 일정한 주기로 콘솔 화면을 갱신하며 출력하는 추상 클래스.
 * <p>
 * 매 주기마다 {@link #print(PrintWriter)}를 호출해 출력을 누적한 뒤,
 * ANSI escape 시퀀스로 콘솔 화면을 지우고 새로 출력하여 "주기적으로 새로고침되는 화면" 효과를 만든다.
 * 깜빡임을 줄이기 위해 한 주기 분량의 출력을 먼저 메모리 버퍼에 쌓은 다음 한꺼번에 표준 출력으로 내보낸다.
 * <p>
 * 사용자가 {@code q}, {@code Q} 또는 ESC 키를 누르면 {@link ConsoleQuitKeyDetector}가
 * 이를 감지해 현재 실행 중인 스레드를 인터럽트하여 루프를 종료한다.
 * 종료 후에는 입력 감지기를 비동기로 취소한다.
 * <p>
 * {@link #setVerbose(boolean) verbose} 모드를 켜면 각 주기마다 소요 시간의 이동평균을 함께 출력한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PeriodicRefreshingConsole extends PeriodicLoopExecution<Void> {
	private static final List<Character> QUIT_CHARACTERS = List.of('q', 'Q', '\u001B'); // 'ESC'
	/** 콘솔 화면을 지우고 커서를 좌상단으로 이동시키는 ANSI escape 시퀀스. */
	private static final String CLEAR_CONSOLE_CONTROL = "\033[2J\033[1;1H";
	/** 소요 시간 이동평균에 사용할 기본 평활 계수. */
	private static final float DEFAULT_MAVG_ALPHA = 0.1f;

	private ConsoleQuitKeyDetector m_quitDetector = new ConsoleQuitKeyDetector(QUIT_CHARACTERS);
	private final MovingAverage m_mavg = new MovingAverage(DEFAULT_MAVG_ALPHA);
	private boolean m_verbose = false;

	/**
	 * 매 주기마다 콘솔에 표시할 내용을 인자로 전달된 {@link PrintWriter}에 출력한다.
	 * <p>
	 * 구현체는 표준 출력에 직접 쓰지 말고 반드시 인자로 받은 {@code pw}에만 출력해야 한다.
	 * 메서드 호출이 끝나면 누적된 내용이 한꺼번에 콘솔로 flush 된다.
	 * <p>
	 * 출력 도중 발생한 일반적인 예외는 구현체가 직접 처리해 한 주기의 출력으로 표시하는 것이 권장된다.
	 * 다만 다음 세 종류의 예외는 루프 자체를 중단시킬 의도가 있을 때 던질 수 있다.
	 * <ul>
	 *   <li>{@link InterruptedException} — 출력 도중 스레드가 인터럽트된 경우.
	 *       사용자의 종료 키 입력으로 인터럽트가 발생하면 그대로 전파하면 된다.</li>
	 *   <li>{@link CancellationException} — 외부에서 루프 취소가 요청된 경우.</li>
	 *   <li>{@link ExecutionException} — 비동기 작업을 기다리다 결과가 실패한 경우.</li>
	 * </ul>
	 * 이 세 예외 중 하나가 던져지면 {@link #performPeriodicAction(long)}가 루프를 정상 종료시킨다.
	 *
	 * @param pw 한 주기 분량의 출력을 받을 {@link PrintWriter}.
	 * @throws InterruptedException  출력 도중 스레드가 인터럽트된 경우.
	 * @throws CancellationException 외부에서 루프 취소가 요청된 경우.
	 * @throws ExecutionException    비동기 작업의 결과가 실패한 경우.
	 */
	abstract protected void print(PrintWriter pw)
		throws InterruptedException, CancellationException, TimeoutException, ExecutionException;

	/**
	 * 갱신 주기를 지정하여 인스턴스를 생성한다.
	 *
	 * @param repeatInterval 화면 갱신 주기.
	 */
	public PeriodicRefreshingConsole(Duration repeatInterval) {
		super(repeatInterval);
	}

	/**
	 * 루프 종료를 트리거할 문자 후보 목록을 변경한다.
	 *
	 * @param quitChars 종료 트리거로 사용할 문자 목록.
	 */
	public void setQuitCharacterCandidates(List<Character> quitChars) {
		m_quitDetector.setQuitCharacters(quitChars);
	}

	/**
	 * 종료 문자가 감지되었을 때 실행할 콜백을 설정한다.
	 * <p>
	 * 기본적으로는 {@link #initializeLoop()}에서 현재 스레드를 인터럽트하는 콜백이 설정된다.
	 *
	 * @param callback 종료 시 호출할 콜백.
	 */
	public void setQuitCallback(Runnable callback) {
		m_quitDetector.setCancelCallback(callback);
	}

	/**
	 * verbose 모드 사용 여부를 설정한다.
	 * <p>
	 * verbose 모드가 켜져 있으면 각 주기마다 출력에 걸린 시간의 이동평균을 함께 표시한다.
	 *
	 * @param verbose verbose 모드 사용 여부.
	 */
	public void setVerbose(boolean verbose) {
		m_verbose = verbose;
	}

	/**
	 * 루프 시작 전 초기화. 현재 스레드를 종료 콜백으로 등록하고 입력 감지기를 시작한다.
	 *
	 * @throws Exception 초기화 도중 발생한 예외.
	 */
	@Override
	protected void initializeLoop() throws Exception {
		final Thread currentThread = Thread.currentThread();
		setQuitCallback(currentThread::interrupt);

		m_quitDetector.startAsync();
	}

	/**
	 * 한 주기 분량의 출력을 수행한다.
	 * <p>
	 * 메모리 버퍼에 {@link #print(PrintWriter)} 결과를 누적한 뒤, 화면 클리어 시퀀스와 함께
	 * 단일 {@code System.out.print} 호출로 한 번에 내보내 깜빡임을 최소화한다.
	 * 버퍼는 UTF-8로 인코딩된다.
	 * verbose 모드일 때는 소요 시간의 이동평균을 마지막 줄에 함께 추가한다.
	 * 출력 후 취소 요청 또는 스레드 인터럽트가 감지되면 {@link CancellationException}을 던져 루프를 종료한다.
	 *
	 * @param loopIndex 현재 루프 인덱스(0부터 시작).
	 * @return 루프를 계속하려면 {@link FOption#empty()},
	 * 			중단시키려면 {@link InterruptedException} 또는 {@link CancellationException} 던짐.
	 * @throws TimeoutException			작업이 시간 제한을 넘긴 경우.
	 * @throws ExecutionException		비동기 작업의 결과가 실패한 경우.
	 */
	@Override
	protected FOption<Void> performPeriodicAction(long loopIndex)
		throws InterruptedException, CancellationException, TimeoutException, ExecutionException {
		try ( ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintWriter pw = new PrintWriter(baos, false, StandardCharsets.UTF_8) ) {
			long startNanos = m_verbose ? System.nanoTime() : 0L;

			print(pw);
			pw.flush();

			StringBuilder frame = new StringBuilder(CLEAR_CONSOLE_CONTROL)
											.append(baos.toString(StandardCharsets.UTF_8));

			if ( m_verbose ) {
				double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
				double avg = m_mavg.observe(elapsedSec);
				String secStr = UnitUtils.toMillisString(Math.round(avg * 1000));
				frame.append("elapsed: ").append(secStr).append(System.lineSeparator());
			}

			System.out.print(frame);
		}
		catch ( IOException e ) {
			// ByteArrayOutputStream은 IOException을 던지지 않으므로 이 블록은 사실상 실행되지 않는다.
			// 하지만 PrintWriter가 IOException을 던질 수 있으므로 일단 잡아서 루프를 계속한다.
			throw new ExecutionException("failed to capture console output", e);
		}

		// 외부에서 cancel 요청이 왔거나 현재 스레드가 인터럽트된 경우 루프 종료
		if ( isCancelRequested() || Thread.currentThread().isInterrupted() ) {
			throw new CancellationException("Console refreshing loop cancelled");
		}
		return FOption.empty();
	}
	
	/**
	 * 루프 종료 후 정리 작업. 입력 감지기 종료를 비동기로 트리거한다.
	 * <p>
	 * {@link ConsoleQuitKeyDetector#stopAsync()}는 즉시 반환하며 내부적으로 진행 중인
	 * 터미널을 닫아 blocking read를 깨운다. 메인 종료 흐름을 막지 않는다.
	 */
	@Override
	protected void finalizeLoop() {
		m_quitDetector.stopAsync();
	}
}
