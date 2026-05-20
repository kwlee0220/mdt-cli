package mdt.cli.set;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */

@Command(
	name="set",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Update MDT entity (element, parameter, argument, file)",
	subcommands= {
		SetElementCommand.class,
		SetParameterCommand.class,
		SetArgumentCommand.class,
		SetFileCommand.class,
	})
public class SetCommands extends CommandCollection {
}
