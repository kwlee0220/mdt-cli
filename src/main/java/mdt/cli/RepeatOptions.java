package mdt.cli;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.CancellationException;

import org.jetbrains.annotations.Nullable;

import utils.StopWatch;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;


/**
 * 조회 명령의 {@code --repeat} 옵션과, 그에 따른 한 번 출력 / 주기적 갱신 출력을 처리하는 picocli mixin.
 * <p>
 * 명령은 이 클래스를 {@code @Mixin}으로 넣고, 출력 동작을 {@link #run(ConsoleAction, String, boolean)}에 넘긴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RepeatOptions {
	@Spec(Spec.Target.MIXEE) private CommandSpec m_mixee;
	
	/**
	 * 한 화면을 출력하는 동작.
	 */
	@FunctionalInterface
	public interface ConsoleAction {
		/**
		 * 출력 내용을 {@code pw}에 쓴다.
		 *
		 * @param pw	출력 대상.
		 * @throws Exception	출력할 내용을 얻지 못한 경우.
		 */
		public void print(PrintWriter pw) throws Exception;
	}

	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\")")
	private Duration m_interval = null;

	/**
	 * 반복 간격을 반환한다.
	 *
	 * @return	반복 간격. {@code --repeat}가 지정되지 않았으면 {@code null}.
	 */
	public @Nullable Duration getInterval() {
		return m_interval;
	}

	/**
	 * {@code --repeat}가 없으면 한 번 출력하고, 있으면 그 간격으로 화면을 갱신하며 반복 출력한다.
	 * <p>
	 * 한 번 출력할 때 {@code action}의 예외는 그대로 전달된다. 반복 출력할 때는 예외를 화면에
	 * {@code failed to <failedTo>: cause=<예외>}로 출력하고 다음 갱신을 기다리며, 사용자가 종료 키를 누르면 끝난다.
	 *
	 * @param action	한 화면을 출력하는 동작.
	 * @param failedTo	반복 출력 중 오류 메시지에 쓸 작업 설명(예: {@code "list MDTInstances"}).
	 * @param verbose	소요 시간 출력 여부. 한 번 출력할 때는 표준 에러로, 반복 출력할 때는 화면 아래에 출력한다.
	 * @throws Exception	한 번 출력하는 중 {@code action}이 던진 예외.
	 */
	public void run(ConsoleAction action, String failedTo, boolean verbose) throws Exception {
		if ( m_interval == null ) {
			StopWatch watch = StopWatch.start();
			try ( PrintWriter pw = AbstractCommand.closeShielded(m_mixee.commandLine().getOut()) ) {
				action.print(pw);
			}
			watch.stop();
			// 표준 출력의 CSV·JSON을 그대로 쓸 수 있도록 소요 시간은 표준 에러로 출력한다.
			if ( verbose ) {
				m_mixee.commandLine().getErr().printf("elapsed: %.3f%n", watch.getElapsedInFloatingSeconds());
			}
			return;
		}

		PeriodicRefreshingConsole console = new PeriodicRefreshingConsole(m_interval) {
			@Override
			protected void print(PrintWriter pw) throws InterruptedException {
				try {
					action.print(pw);
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw e;
				}
				catch ( Exception e ) {
					pw.println("failed to " + failedTo + ": cause=" + e);
				}
			}
		};
		console.setVerbose(verbose);
		try {
			console.run();
		}
		catch ( CancellationException | InterruptedException e ) {
			// 사용자가 종료 키를 눌러 반복이 끝난 경우.
		}
	}
}
