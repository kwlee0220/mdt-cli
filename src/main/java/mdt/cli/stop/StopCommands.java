package mdt.cli.stop;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name="stop",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Stop MDT entities (e.g., MDT instance, workflow, etc.)",
	subcommands= {
		StopMDTInstanceCommand.class,
		StopWorkflowCommand.class,
	})
public class StopCommands extends CommandCollection {}