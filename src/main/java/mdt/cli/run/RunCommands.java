package mdt.cli.run;

import picocli.CommandLine.Command;

import mdt.cli.CommandCollection;
import mdt.task.builtin.SetTaskCommand;


/**
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
		SetTaskCommand.class,
		RunSubmodelCommand.class,
		RESTfulOperationCommand.class,
	})
public class RunCommands extends CommandCollection {}