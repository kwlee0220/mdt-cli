package mdt.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;

import utils.Throwables;
import utils.http.RESTfulException;

import mdt.model.MDTException;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;


/**
 * MDT CLI 명령 실행 중 발생한 예외를 출력하고 종료 코드를 정하는 picocli 예외 처리기.
 * <p>
 * 사용자가 원인을 바로 알 수 있는 오류(없는 자원, 잘못된 인자, 원격 서버 오류, 입출력 오류, 시간 초과)는
 * {@code error: <메시지>} 한 줄만 표준 에러로 출력한다. 그 밖의 예외(프로그램 오류로 보이는 것)와,
 * {@code mdt} 로거가 DEBUG 이하로 설정된 경우({@code --loglevel debug})는 스택 트레이스를 출력한다.
 * 종료 코드는 picocli의 실행 오류 코드({@link CommandLine.Model.CommandSpec#exitCodeOnExecutionException()},
 * 기본값 1)를 쓴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExceptionHandler implements IExecutionExceptionHandler {
	@Override
	public int handleExecutionException(Exception ex, CommandLine cmdLine, ParseResult parseResult) {
		Throwable cause = Throwables.unwrapThrowable(ex);
		PrintWriter err = cmdLine.getErr();
		if ( isUserError(cause) && !LoggerFactory.getLogger("mdt").isDebugEnabled() ) {
			err.println("error: " + describe(cause));
		}
		else {
			cause.printStackTrace(err);
		}
		err.flush();

		return cmdLine.getCommandSpec().exitCodeOnExecutionException();
	}

	private static boolean isUserError(Throwable cause) {
		return cause instanceof MDTException
			|| cause instanceof IllegalArgumentException
			|| cause instanceof RESTfulException
			|| cause instanceof IOException
			|| cause instanceof TimeoutException;
	}

	private static String describe(Throwable cause) {
		String msg = cause.getMessage();
		String desc = (msg != null && !msg.isBlank()) ? msg : cause.getClass().getSimpleName();

		// 네트워크 오류 등은 감싼 예외의 메시지가 실제 원인이므로 함께 보여 준다.
		Throwable inner = cause.getCause();
		if ( inner != null && inner != cause && inner.getMessage() != null && !desc.contains(inner.getMessage()) ) {
			desc = desc + ": " + inner.getMessage();
		}
		return desc;
	}
}
