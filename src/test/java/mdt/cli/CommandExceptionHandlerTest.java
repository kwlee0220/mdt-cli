package mdt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import utils.http.RESTfulIOException;

import mdt.model.InvalidResourceStatusException;
import mdt.model.ResourceAlreadyExistsException;
import mdt.model.ResourceNotFoundException;
import mdt.model.instance.MDTInstanceStatus;


/**
 * {@link CommandExceptionHandler#describe(Throwable)}의 한 줄 오류 메시지 형식 테스트.
 */
public class CommandExceptionHandlerTest {
	@Test
	public void resourceNotFound() {
		Throwable ex = new ResourceNotFoundException("MDTInstance", "id=CNC-04");
		assertEquals("ResourceNotFoundException: id=MDTInstance(id=CNC-04)",
					CommandExceptionHandler.describe(ex));
	}

	@Test
	public void resourceAlreadyExists() {
		assertEquals("ResourceAlreadyExistsException: id=MDTInstance(id=CNC-04)",
					CommandExceptionHandler.describe(
								new ResourceAlreadyExistsException("MDTInstance", "id=CNC-04")));
	}

	@Test
	public void invalidResourceStatus() {
		Throwable ex = new InvalidResourceStatusException("MDTInstance", "id=CNC-04",
															MDTInstanceStatus.STOPPED);
		assertEquals("InvalidResourceStatusException: id=MDTInstance(id=CNC-04), status=STOPPED",
					CommandExceptionHandler.describe(ex));
	}

	@Test
	public void remoteResourceExceptionKeepsFormat() {
		// 서버 오류에서 메시지만으로 되살린 예외도 같은 형식으로 출력한다.
		Throwable notFound = new ResourceNotFoundException("MDTInstance(id=CNC-04)");
		assertEquals("ResourceNotFoundException: id=MDTInstance(id=CNC-04)",
					CommandExceptionHandler.describe(notFound));
		assertEquals("InvalidResourceStatusException: id=MDTInstance(id=CNC-04), status=RUNNING",
					CommandExceptionHandler.describe(
								new InvalidResourceStatusException("MDTInstance(id=CNC-04): RUNNING")));
	}

	@Test
	public void resourceExceptionWithFreeMessage() {
		assertEquals("ResourceNotFoundException: no such thing",
					CommandExceptionHandler.describe(new ResourceNotFoundException("no such thing")));
	}

	@Test
	public void otherExceptions() {
		assertEquals("error: IllegalArgumentException: bad arg",
					CommandExceptionHandler.describe(new IllegalArgumentException("bad arg")));
		assertEquals("error: IOException", CommandExceptionHandler.describe(new IOException()));
		assertEquals("error: RESTfulIOException: connection refused",
					CommandExceptionHandler.describe(new RESTfulIOException("connection refused")));
		// 감싼 예외의 메시지가 바깥 메시지에 없으면 덧붙인다.
		assertEquals("error: IllegalStateException: load failed: disk full",
					CommandExceptionHandler.describe(new IllegalStateException("load failed",
																	new IOException("disk full"))));
	}
}
