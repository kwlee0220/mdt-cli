package mdt.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import utils.Throwables;
import utils.http.RESTfulException;

import mdt.model.InvalidResourceStatusException;
import mdt.model.MDTException;
import mdt.model.ResourceAlreadyExistsException;
import mdt.model.ResourceNotFoundException;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;


/**
 * MDT CLI 명령 실행 중 발생한 예외를 출력하고 종료 코드를 정하는 picocli 예외 처리기.
 * <p>
 * 사용자가 원인을 바로 알 수 있는 오류(없는 자원, 잘못된 인자, 원격 서버 오류, 입출력 오류, 시간 초과)는
 * 표준 에러로 한 줄만 출력한다.
 * <ul>
 *   <li>자원 오류는 예외 이름과 대상 자원을 출력한다.
 *     <ul>
 *       <li>{@code ResourceNotFoundException: id=MDTInstance(id=CNC-04)}</li>
 *       <li>{@code ResourceAlreadyExistsException: id=MDTInstance(id=CNC-04)}</li>
 *       <li>{@code InvalidResourceStatusException: id=MDTInstance(id=CNC-04), status=STOPPED}</li>
 *     </ul>
 *     메시지가 {@code <자원 종류>(<식별자>)} 형식이 아니면 {@code <예외 이름>: <메시지>}로 출력한다.</li>
 *   <li>그 밖의 오류는 {@code error: <예외 이름>: <메시지>}로 출력한다. 감싼 예외의 메시지가 따로 있으면
 *       {@code : <감싼 예외 메시지>}를 덧붙인다.</li>
 * </ul>
 * 그 밖의 예외(프로그램 오류로 보이는 것)와, {@code mdt} 로거가 DEBUG 이하로 설정된 경우
 * ({@code --loglevel debug})는 스택 트레이스를 출력한다.
 * 종료 코드는 picocli의 실행 오류 코드({@link CommandLine.Model.CommandSpec#exitCodeOnExecutionException()},
 * 기본값 1)를 쓴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExceptionHandler implements IExecutionExceptionHandler {
	/** 자원 예외의 메시지 형식 {@code <자원 종류>(<식별자>)}. */
	private static final Pattern RESOURCE_ID = Pattern.compile("\\w+\\(.*\\)", Pattern.DOTALL);
	/** {@link InvalidResourceStatusException}의 메시지 형식 {@code <자원 종류>(<식별자>): <상태>}. */
	private static final Pattern RESOURCE_STATUS = Pattern.compile("(\\w+\\(.*\\)): (\\S+)", Pattern.DOTALL);

	@Override
	public int handleExecutionException(Exception ex, CommandLine cmdLine, ParseResult parseResult) {
		Throwable cause = Throwables.unwrapThrowable(ex);
		PrintWriter err = cmdLine.getErr();
		if ( isUserError(cause) && !LoggerFactory.getLogger("mdt").isDebugEnabled() ) {
			err.println(describe(cause));
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

	/**
	 * 사용자 오류로 분류된 예외를 한 줄 메시지로 만든다.
	 *
	 * @param cause	출력할 예외.
	 * @return	출력할 한 줄 메시지.
	 */
	static String describe(Throwable cause) {
		String name = cause.getClass().getSimpleName();
		String msg = cause.getMessage();
		if ( cause instanceof InvalidResourceStatusException ) {
			Matcher m = (msg != null) ? RESOURCE_STATUS.matcher(msg) : null;
			if ( m != null && m.matches() ) {
				return String.format("%s: id=%s, status=%s", name, m.group(1), m.group(2));
			}
			return name + ": " + msg;
		}
		if ( cause instanceof ResourceNotFoundException || cause instanceof ResourceAlreadyExistsException ) {
			if ( msg != null && RESOURCE_ID.matcher(msg).matches() ) {
				return name + ": id=" + msg;
			}
			return name + ": " + msg;
		}

		String desc = (msg != null && !msg.isBlank()) ? name + ": " + msg : name;
		// 네트워크 오류 등은 감싼 예외의 메시지가 실제 원인이므로 함께 보여 준다.
		Throwable inner = cause.getCause();
		if ( inner != null && inner != cause && inner.getMessage() != null
			&& !desc.contains(inner.getMessage()) ) {
			desc = desc + ": " + inner.getMessage();
		}
		return "error: " + desc;
	}
}
