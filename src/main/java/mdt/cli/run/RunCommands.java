package mdt.cli.run;

import mdt.cli.CommandCollection;
import mdt.task.builtin.SetTaskCommand;

import picocli.CommandLine.Command;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name="run",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="Run MDT Tasks (e.g., AAS operation, submodel, etc.)",
	subcommands= {
		RunAASOperationCommand.class,
		SetTaskCommand.class,
		RunSubmodelCommand.class,
	})
public class RunCommands extends CommandCollection {}