package mdt.cli;

import java.io.File;
import java.time.Duration;

import utils.LogbackConfigLoader;
import utils.Picoclies;
import utils.Utilities;
import utils.io.FileUtils;

import mdt.cli.MDTCommandsMain.ResumeCommands;
import mdt.cli.MDTCommandsMain.SimulationCommands;
import mdt.cli.MDTCommandsMain.SuspendCommands;
import mdt.cli.add.AddCommands;
import mdt.cli.get.GetCommands;
import mdt.cli.list.ListCommands;
import mdt.cli.remove.RemoveCommands;
import mdt.cli.resolve.ResolveCommands;
import mdt.cli.run.RunCommands;
import mdt.cli.set.SetCommands;
import mdt.cli.start.StartCommands;
import mdt.cli.stop.StopCommands;
import mdt.cli.workflow.ResumeWorkflowCommand;
import mdt.cli.workflow.SuspendWorkflowCommand;
import mdt.client.MDTClientConfig;

import ch.qos.logback.classic.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(name="mdt",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="%nManufacturing DigitalTwin (MDT) related commands.",
	versionProvider = MDTVersionProvider.class,
	subcommands = {
		ShowClientConfigCommand.class,
		BuildInstanceCommand.class,
		ListCommands.class,
		RemoveCommands.class,
		StartCommands.class,
		StopCommands.class,
		SuspendCommands.class,
		ResumeCommands.class,
		AddCommands.class,	
		GetCommands.class,
		SetCommands.class,
		RunCommands.class,
		ResolveCommands.class,
		SimulationCommands.class,
	})
public class MDTCommandsMain {
	private static final String LOGBACK_CONFIG_FILE = "logback.xml";
	
	
	public static final void main(String... args) throws Exception {
		configureLogging();
		System.exit(newCommandLine(new MDTCommandsMain()).execute(args));
	}
	
	/**
	 * 클라이언트 홈 디렉토리({@link MDTClientConfig#getClientHomeDir()})의 {@code logback.xml}로 로그 설정을 읽는다.
	 * 파일이 없으면 jar에 포함된 기본 설정(클래스패스 루트의 {@code /logback.xml}, 표준 에러로 INFO 이상 출력)을 읽는다.
	 * <p>
	 * {@code mdt} 진입점과 개별 명령의 단독 실행이 같은 로그 설정을 쓰도록 둘 다 이 메소드를 부른다.
	 *
	 * @throws Exception	로그 설정을 읽지 못한 경우.
	 */
	public static void configureLogging() throws Exception {
		File logbackConfigFile = new File(MDTClientConfig.getClientHomeDir(), LOGBACK_CONFIG_FILE);
		if ( logbackConfigFile.exists() && logbackConfigFile.isFile() ) {
			LogbackConfigLoader.loadLogbackConfigFromFile(logbackConfigFile);
		}
		else {
			// 클래스패스 루트에서 찾는다(상대 이름이면 mdt/cli/ 아래를 찾는다).
			LogbackConfigLoader.loadLogbackConfigFromClass(MDTCommandsMain.class, "/" + LOGBACK_CONFIG_FILE);
		}
	}
	
	/**
	 * MDT CLI 공통 설정을 적용한 {@link CommandLine}을 만든다.
	 * <p>
	 * {@code mdt} 진입점과 개별 명령의 단독 실행({@link AbstractCommand#main(AbstractCommand, String...)})이
	 * 같은 설정을 쓰도록 한 곳에 모은 것이다. 적용되는 설정은 다음과 같다.
	 * <ul>
	 *   <li>enum 값 대소문자 구분 없음, 옵션·하위 명령 약어 허용</li>
	 *   <li>{@link Duration}, {@link Level} 타입 변환기 등록</li>
	 *   <li>사용법 너비 110 컬럼, ANSI 색상 끔</li>
	 *   <li>실행 중 예외는 {@link CommandExceptionHandler}가 출력하고 종료 코드를 정한다.</li>
	 * </ul>
	 *
	 * @param command	picocli 명령 객체.
	 * @return	설정이 적용된 {@link CommandLine}.
	 */
	public static CommandLine newCommandLine(Object command) {
		return newCommandLine(command, CommandLine.defaultFactory());
	}
	
	/**
	 * {@link #newCommandLine(Object)}과 같되, 하위 명령 객체를 {@code factory}로 만든다.
	 * <p>
	 * 테스트가 하위 명령을 만들 때 가짜 MDTManager 접속 함수를 넣는 데 쓴다.
	 *
	 * @param command	picocli 명령 객체.
	 * @param factory	하위 명령 객체를 만드는 팩토리.
	 * @return	설정이 적용된 {@link CommandLine}.
	 */
	static CommandLine newCommandLine(Object command, CommandLine.IFactory factory) {
		return new CommandLine(command, factory)
					.setCaseInsensitiveEnumValuesAllowed(true)
					.setAbbreviatedOptionsAllowed(true)
					.setAbbreviatedSubcommandsAllowed(true)
					.registerConverter(Duration.class, new Picoclies.DurationConverter())
					.registerConverter(Level.class, new Picoclies.LogLevelConverter())
					.setUsageHelpWidth(110)
					.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF))
					.setExecutionExceptionHandler(new CommandExceptionHandler());
	}
	
	@Command(
		name="simulation",
		parameterListHeading = "Parameters:%n",
		optionListHeading = "Options:%n",
		mixinStandardHelpOptions = true,
		description="Simulation related commands",
		subcommands= {
			StartSkkuSimulationCommand.class,
		})
	public static class SimulationCommands extends CommandCollection {}
	
	@Command(
		name="suspend",
		parameterListHeading = "Parameters:%n",
		optionListHeading = "Options:%n",
		mixinStandardHelpOptions = true,
		description="Suspend MDT entities (e.g., workflow)",
		subcommands= {
			SuspendWorkflowCommand.class,
		})
	public static class SuspendCommands extends CommandCollection {}
	
	@Command(
		name="resume",
		parameterListHeading = "Parameters:%n",
		optionListHeading = "Options:%n",
		mixinStandardHelpOptions = true,
		description="Resume suspended MDT entities (e.g., workflow)",
		subcommands= {
			ResumeWorkflowCommand.class,
		})
	public static class ResumeCommands extends CommandCollection {}
}
