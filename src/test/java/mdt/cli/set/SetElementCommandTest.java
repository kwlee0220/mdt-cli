package mdt.cli.set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;


/**
 * {@code mdt set element}가 대상을 MDT 표현식 위치 인자 하나로 받는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SetElementCommandTest {
	private static String expr(SetElementCommand cmd) throws ReflectiveOperationException {
		var field = SetElementCommand.class.getDeclaredField("m_elementExpr");
		field.setAccessible(true);
		return (String)field.get(cmd);
	}

	private static SetElementCommand parse(String... args) {
		SetElementCommand cmd = new SetElementCommand();
		new CommandLine(cmd).parseArgs(args);
		return cmd;
	}

	@Test
	public void takesOneExpression() throws Exception {
		assertEquals("param:test:Data", expr(parse("param:test:Data", "--json", "1")));
		assertEquals("test:Data:DataInfo.Temp", expr(parse("--json", "\"x\"", "test:Data:DataInfo.Temp")));
	}

	@Test
	public void rejectsTheOldForm() {
		assertThrows(ParameterException.class, () -> parse("test", "Data", "DataInfo", "--json", "1"));
	}
}
