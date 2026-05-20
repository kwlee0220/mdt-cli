package mdt.cli;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;

import utils.Preconditions;
import utils.func.Unchecked;
import utils.io.IOUtils;


/**
 * 콘솔에서 지정된 종료 문자 입력을 감지하는 비동기 실행 컴포넌트.
 * <p>
 * Guava의 {@link AbstractExecutionThreadService}를 기반으로 별도의 스레드에서
 * 터미널을 raw 모드로 전환한 후, 사용자가 입력하는 문자를 한 글자씩 읽어 미리
 * 등록된 종료 문자 목록({@code quitCharacters}) 중 하나와 일치하면 실행을 종료한다.
 * 주로 장시간 실행되는 CLI 작업에서 사용자가 특정 키를 눌러 작업을 중단할 수
 * 있도록 하는 용도로 사용된다.
 * <p>
 * 외부에서 {@link #stopAsync()}가 호출되면 {@link #triggerShutdown()}이 진행 중인
 * {@link Terminal}을 닫아 blocking된 read를 깨우는 방식으로 협력적 취소를 수행한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class ConsoleQuitKeyDetector extends AbstractExecutionThreadService {
	private volatile List<Character> m_quitCharacters;
	private volatile Terminal m_terminal = null;
	private volatile char m_input = '\0';

	/**
	 * 주어진 종료 문자 목록을 사용하는 {@code ConsoleQuitKeyDetector}를 생성한다.
	 *
	 * @param stopChars	감지 대상이 되는 종료 문자 목록. 사용자가 이 목록에 포함된
	 * 					문자를 입력하면 detector의 실행이 종료된다.
	 */
	public ConsoleQuitKeyDetector(List<Character> stopChars) {
		Preconditions.checkNotNullArgument(stopChars, "stopChars is null");
		m_quitCharacters = stopChars;
	}

	/**
	 * 감지 대상이 되는 종료 문자 목록을 변경한다.
	 *
	 * @param stopChars	새로운 종료 문자 목록.
	 */
	public void setQuitCharacters(List<Character> stopChars) {
		Preconditions.checkNotNullArgument(stopChars, "stopChars is null");
		m_quitCharacters = stopChars;
	}

	/**
	 * 실행이 종료된 시점에 호출될 콜백을 등록한다.
	 * <p>
	 * 등록된 콜백은 사용자가 종료 문자를 입력하여 정상적으로 종료된 경우와
	 * 외부에서 {@link #stopAsync()}로 중단된 경우, 그리고 실행 중 예외로 실패한
	 * 경우 모두 호출된다. 콜백 실행 중 발생한 예외는 모두 무시된다.
	 * <p>
	 * 본 메소드는 매 호출마다 {@link Service.Listener}를 추가하므로 동일 콜백을
	 * 중복 등록하지 않도록 주의한다.
	 *
	 * @param callback	종료 시 호출될 콜백.
	 */
	public void setCancelCallback(Runnable callback) {
		Executor executor = MoreExecutors.directExecutor();
		addListener(new Listener() {
			@Override
			public void terminated(State from) {
				Unchecked.runOrIgnore(callback::run);
			}
			@Override
			public void failed(State from, Throwable failure) {
				Unchecked.runOrIgnore(callback::run);
			}
		}, executor);
	}

	/**
	 * 사용자가 입력한 종료 문자를 반환한다.
	 * <p>
	 * 종료 문자가 아직 입력되지 않은 상태(취소 또는 실행 중)에서 호출되면
	 * 초기값인 {@code '\0'}이 반환된다.
	 *
	 * @return 감지된 종료 문자. 종료 문자가 입력되지 않았다면 {@code '\0'}.
	 */
	public char getDetectedInput() {
		return m_input;
	}

	/**
	 * 콘솔 입력 감지 루프를 수행한다.
	 * <p>
	 * 터미널을 raw 모드로 전환한 후, 서비스가 RUNNING 상태인 동안 한 문자씩 읽어
	 * 종료 문자 목록에 포함된 문자가 입력되는지 확인한다. 일치하는 문자가
	 * 입력되면 해당 문자를 저장하고 정상 종료한다.
	 * <p>
	 * 외부에서 {@link #triggerShutdown()}이 호출되면 진행 중인 {@link Terminal}이
	 * 닫혀 blocking read가 깨어나며, 루프 조건이 false가 되어 자연스럽게 종료된다.
	 *
	 * @throws Exception 터미널 초기화 또는 입력 처리 중 오류가 발생한 경우.
	 */
	@Override
	protected void run() throws Exception {
		try ( Terminal terminal = TerminalBuilder.terminal() ) {
			m_terminal = terminal;
			terminal.enterRawMode();

			while ( isRunning() ) {
				int ch;
				try {
					ch = terminal.reader().read();
				}
				catch ( IOException e ) {
					if ( !isRunning() ) break;
					throw e;
				}
				if ( ch < 0 ) break;

				char input = (char)ch;
				if ( m_quitCharacters.contains(input) ) {
					m_input = input;
					return;
				}
			}
		}
		finally {
			m_terminal = null;
		}
	}

	/**
	 * 진행 중인 입력 감지 작업의 취소를 트리거한다.
	 * <p>
	 * 워커 스레드가 blocking된 {@code read()}에서 깨어날 수 있도록 진행 중인
	 * {@link Terminal}을 닫는다. 워커는 깨어난 직후 {@link #isRunning()}이 false임을
	 * 감지하여 루프를 빠져나가고 서비스는 TERMINATED 상태로 전이한다.
	 */
	@Override
	protected void triggerShutdown() {
		IOUtils.closeQuietly(m_terminal);
	}
}
