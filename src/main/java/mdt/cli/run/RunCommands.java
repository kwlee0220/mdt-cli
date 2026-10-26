package mdt.cli.run;

import picocli.CommandLine.Command;

import mdt.cli.CommandCollection;


/**
 * MDT 태스크 실행 관련 하위 커맨드들을 모은 커맨드 그룹({@code mdt run}).
 * <p>
 * AAS Operation element 호출({@code operation}), 연산 Submodel 호출({@code submodel}),
 * RESTful 비동기 RPC 호출({@code restful}), 값 복사({@code set}) 커맨드를 제공한다.
 * 하위 커맨드 없이 실행되면 사용법을 출력한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name="run",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Run MDT Tasks (e.g., AAS operation, submodel, RESTful etc.)",
	subcommands= {
		RunAASOperationCommand.class,
		RunOperationSubmodelCommand.class,
		RESTfulAsyncRpcCommand.class,
		SetTaskCommand.class,
	})
public class RunCommands extends CommandCollection {}