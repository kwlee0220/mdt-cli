package mdt.cli.add;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name="add",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Add an MDT-entity (instance, workflow model, etc.)",
	subcommands= {
		AddMDTInstanceCommand.class,
		AddWorkflowModelCommand.class,
	})
public class AddCommands extends CommandCollection {}