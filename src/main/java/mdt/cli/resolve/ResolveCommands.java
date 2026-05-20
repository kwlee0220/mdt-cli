package mdt.cli.resolve;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */

@Command(
	name="resolve",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Resolve MDT-related target",
	subcommands= {
		ResolveReferenceToUrlCommand.class,
	})
public class ResolveCommands extends CommandCollection { }

