package mdt.task.builtin;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.FOption;

import mdt.task.MDTTask;
import mdt.workflow.model.TaskDescriptor;

import picocli.CommandLine.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractTaskCommand extends MultiVariablesCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractTaskCommand.class);
	
	@Option(names={"--timeout"}, paramLabel="duration", description="Invocation timeout (e.g. \"30s\", \"1m\")")
	protected String m_timeout = null;
	
	public AbstractTaskCommand() {
		setLogger(s_logger);
	}
	
	protected void loadTaskDescriptor(TaskDescriptor descriptor) throws IOException {
		// CommandLine을 참조하여 input/output argument 정보를 읽어온다.
		TaskArgumentsDescriptor taskVarsDesc = loadTaskArgumentsFromCommandLine();
		taskVarsDesc.getInputs().forEach(descriptor::addInputArgumentSpec);
		taskVarsDesc.getOutputs().forEach(descriptor::addOutputArgumentSpec);

		FOption.accept(m_timeout, to -> descriptor.addOption(MDTTask.OPTION_TIMEOUT, to));
	}
}
