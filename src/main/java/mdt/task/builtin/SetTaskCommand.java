package mdt.task.builtin;	

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerAware;
import mdt.model.sm.value.ElementValues;
import mdt.workflow.model.ArgumentSpec;
import mdt.workflow.model.ArgumentSpec.ReferenceArgumentSpec;
import mdt.workflow.model.TaskDescriptor;

import picocli.CommandLine.Command;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "set",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set task execution command."
)
public class SetTaskCommand extends MultiVariablesCommand {
	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		Instant started = Instant.now();
		
		TaskDescriptor descriptor = new TaskDescriptor();
		descriptor.setId("set");
		descriptor.setType(SetTask.class.getName());
		
		TaskArgumentsDescriptor tad = loadTaskArgumentsFromCommandLine();
		
		ArgumentSpec src = tad.getInputs().get(SetTask.ARG_SOURCE);
		src = MDTInstanceManagerAware.activate(src, manager);
		descriptor.addInputArgumentSpec("source", src);
		
		ArgumentSpec tar = tad.getOutputs().get(SetTask.ARG_TARGET);
		if ( tar instanceof ReferenceArgumentSpec tarRef ) {
            tarRef.activate(manager);
            SubmodelElement tarSme = tarRef.read();
            tarSme.setIdShort("target");
    		descriptor.addOutputArgumentSpec("target", tarRef);
            
            SubmodelElement srcSme;
            if ( src instanceof ReferenceArgumentSpec srcRef ) {
                srcSme = srcRef.getElementReference().read();
            }
            else {
                srcSme = tarRef.read();
                srcSme.setIdShort("source");
                ElementValues.update(srcSme, src.readValue());
            }

    		SetTask setTask = new SetTask(descriptor);
    		Map<String,SubmodelElement> outputs = setTask.run(manager);
    		
    		descriptor.updateOutputArguments(manager, outputs, getLogger());
            
            return;
        }
		
		Duration elapsed = Duration.between(started, Instant.now());
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("HttpTask: elapsedTime={}", elapsed);
		}
	}
	public static void main(String... args) throws Exception {
		main(new SetTaskCommand(), args);
	}
}
