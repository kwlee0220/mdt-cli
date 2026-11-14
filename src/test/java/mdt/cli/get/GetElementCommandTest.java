package mdt.cli.get;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;


/**
 * {@code mdt get element}가 대상을 MDT 표현식 위치 인자 하나로 받는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class GetElementCommandTest {
	private static String expr(GetElementCommand cmd) throws ReflectiveOperationException {
		var field = GetElementCommand.class.getDeclaredField("m_elementExpr");
		field.setAccessible(true);
		return (String)field.get(cmd);
	}

	private static GetElementCommand parse(String... args) {
		GetElementCommand cmd = new GetElementCommand();
		new CommandLine(cmd).parseArgs(args);
		return cmd;
	}

	@Test
	public void takesOneExpression() throws Exception {
		assertEquals("param:test:Data", expr(parse("param:test:Data")));
		assertEquals("test:Data:DataInfo.Temp", expr(parse("-o", "json", "test:Data:DataInfo.Temp")));
	}

	@Test
	public void rejectsTheOldForms() {
		assertThrows(ParameterException.class, () -> parse());
		assertThrows(ParameterException.class, () -> parse("test", "Data", "DataInfo"));
		assertThrows(ParameterException.class, () -> parse("--ref", "param:test:Data"));
	}
}
