package mdt.cli.remove;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */

@Command(
	name="remove",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Remove MDT entity (instance, file, workflow model, workflow instance).",
	subcommands= {
		RemoveMDTInstanceCommand.class,
		RemoveFileCommand.class,
		RemoveWorkflowModelCommand.class,
		RemoveWorkflowInstanceCommand.class,
	})
public class RemoveCommands extends CommandCollection {
}
