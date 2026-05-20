package mdt.cli.start;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name="start",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Start MDT entity (instance, workflow, etc.)",
	subcommands= {
		StartMDTInstanceCommand.class,
		StartWorkflowCommand.class,
	})
public class StartCommands extends CommandCollection {}