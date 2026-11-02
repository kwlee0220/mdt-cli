package mdt.cli.run;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.task.SetTask;
import mdt.task.TaskArgument;
import mdt.task.TaskArgument.ReferenceArgument;


/**
 * source 인자의 값을 target 인자가 가리키는 MDT 요소에 복사하는 커맨드.
 * <p>
 * {@code --in.source=<expr>}로 지정된 값을 {@code --out.target=<ref-expr>}가 가리키는
 * MDT 요소에 기록하는 {@link SetTask}를 실행한다. 두 인자는 모두 필수이며, 그 외의 이름으로
 * 지정된 인자는 오류로 처리된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "set",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set task execution command."
)
public class SetTaskCommand extends DynamicArgumentsCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(SetTaskCommand.class);

	public SetTaskCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();

		Instant started = Instant.now();

		TaskCommandArguments cmdArgs = loadTaskArgumentsFromCommandLine();
		checkArgumentNames("input", cmdArgs.getInputs(), Set.of(SetTask.ARG_SOURCE));
		checkArgumentNames("output", cmdArgs.getOutputs(), Set.of(SetTask.ARG_TARGET));
		if ( !cmdArgs.getInoutputs().isEmpty() ) {
			throw new IllegalArgumentException("inoutput arguments are not supported: "
												+ cmdArgs.getInoutputs().keySet());
		}

		// TaskArgument.parseExpr()와 TaskArgument.reference()는 null 표현식에 대해
		// 불친절한 오류를 발생시키기 때문에, 인자 누락 여부를 먼저 확인한다.
		String srcExpr = cmdArgs.getInputs().get(SetTask.ARG_SOURCE);
		if ( srcExpr == null ) {
			throw new IllegalArgumentException("'--in." + SetTask.ARG_SOURCE + "' argument is not specified");
		}
		String tarExpr = cmdArgs.getOutputs().get(SetTask.ARG_TARGET);
		if ( tarExpr == null ) {
			throw new IllegalArgumentException("'--out." + SetTask.ARG_TARGET + "' argument is not specified");
		}

		TaskArgument src = TaskArgument.parseExpr(srcExpr);
		ReferenceArgument tar = TaskArgument.reference(tarExpr);

		SetTask task = SetTask.builder()
							.source(src)
							.target(tar)
							.build();
		task.run(manager);

		Duration elapsed = Duration.between(started, Instant.now());
		getLogger().info("Set[{} -> {}], elapsedTime={}", srcExpr, tarExpr, elapsed);
	}

	public static void main(String... args) throws Exception {
		main(new SetTaskCommand(), args);
		System.exit(0);
	}
}
