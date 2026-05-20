package mdt.cli;

import java.io.File;
import java.time.Duration;

import utils.LogbackConfigLoader;
import utils.Picoclies;
import utils.Utilities;
import utils.io.FileUtils;

import mdt.cli.MDTCommandsMain.SimulationCommands;
import mdt.cli.add.AddCommands;
import mdt.cli.get.GetCommands;
import mdt.cli.list.ListCommands;
import mdt.cli.remove.RemoveCommands;
import mdt.cli.resolve.ResolveCommands;
import mdt.cli.run.RunCommands;
import mdt.cli.set.SetCommands;
import mdt.cli.start.StartCommands;
import mdt.cli.stop.StopCommands;

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
	description="%nManufactoring DigitalTwin (MDT) related commands.",
	version = "1.4.1",
	subcommands = {
		EndpointCommand.class,
		BuildInstanceCommand.class,
		ListCommands.class,
		RemoveCommands.class,
		StartCommands.class,
		StopCommands.class,
		AddCommands.class,	
		GetCommands.class,
		SetCommands.class,
		RunCommands.class,
		ResolveCommands.class,
		SimulationCommands.class,
	})
public class MDTCommandsMain {
	private static final String LOGBACK_CONFIG_FILE = "logback.xml";
	
	public static File getClientHomeDir() {
		return Utilities.getEnvironmentVariableFile("MDT_CLIENT_HOME")
						.orElse(FileUtils.getCurrentWorkingDirectory());
	}
	
	public static final void main(String... args) throws Exception {
		File logbackConfigFile = new File(getClientHomeDir(), LOGBACK_CONFIG_FILE);
		if ( logbackConfigFile.exists() && logbackConfigFile.isFile() ) {
			LogbackConfigLoader.loadLogbackConfigFromFile(logbackConfigFile);
		}
		else {
			LogbackConfigLoader.loadLogbackConfigFromClass(MDTCommandsMain.class, LOGBACK_CONFIG_FILE);
		}
		
		CommandLine cmdLine = new CommandLine(new MDTCommandsMain())
									.setCaseInsensitiveEnumValuesAllowed(true)
									.setAbbreviatedOptionsAllowed(true)
									.setAbbreviatedSubcommandsAllowed(true)
									.registerConverter(Duration.class, new Picoclies.DurationConverter())
									.registerConverter(Level.class, new Picoclies.LogLevelConverter())
									.setUsageHelpWidth(110);
		System.exit(cmdLine.execute(args));
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
		name="manager",
		parameterListHeading = "Parameters:%n",
		optionListHeading = "Options:%n",
		mixinStandardHelpOptions = true,
		description="MDTManager related commands",
		subcommands= {
			ShutdownManagerCommand.class,
		})
	public static class MDTManagerCommands extends CommandCollection {}
}
