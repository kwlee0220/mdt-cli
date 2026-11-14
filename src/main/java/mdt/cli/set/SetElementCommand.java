package mdt.cli.set;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;


/**
 * MDT 표현식이 가리키는 SubmodelElement에 값을 쓰는 CLI 명령.
 * <p>
 * 대상은 위치 인자 하나로 받은 MDT 표현식이다(예: {@code test:Data:DataInfo.Temperature},
 * {@code param:test:Data}, {@code oparg:test:AI:in:Image}). 값은 {@code --json}, {@code --json-file} 또는
 * {@code --file}로 지정한다({@link AbstractSetCommand}). 조회 전용 참조({@code timeseries:} 등)는 실행할 때
 * 오류가 난다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "element",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set SubmodelElement value."
)
public class SetElementCommand extends AbstractSetCommand {
	@Parameters(index="0", paramLabel="element-expr",
				description="MDT element expression "
							+ "(e.g. inst:Data:DataInfo.Temperature, param:inst:Data, oparg:inst:AI:in:Image)")
	private String m_elementExpr;

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		run(manager, ElementReferences.parseExpr(m_elementExpr));
	}

	public static void main(String... args) throws Exception {
		main(new SetElementCommand(), args);
	}
}
