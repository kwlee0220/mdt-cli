package mdt.cli;

import java.util.Collections;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.client.HttpMDTManager;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTOperationDescriptor;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Completions {
	private Completions() {
		throw new AssertionError("Should not be called: class=" + Completions.class.getName());
	}
	
	private static final Logger s_logger = LoggerFactory.getLogger(Completions.class);

	public static class RunningInstanceIdCompletions implements Iterable<String> {
		@Override
		public Iterator<String> iterator() {
			try {
				HttpMDTManager mdt = HttpMDTManager.connectWithDefault();
				HttpMDTInstanceManager manager = mdt.getInstanceManager();
				return FStream.from(manager.getInstanceAll())
								.filter(inst -> inst.isRunning())
								.map(MDTInstance::getId)
								.iterator();
			}
			catch ( Exception e ) {
	            s_logger.warn("failed to get MDT instance ids", e);
	            return Collections.emptyIterator();
	        }
		}
	}

	public static class NonRunningInstanceIdCompletions implements Iterable<String> {
		@Override
		public Iterator<String> iterator() {
			try {
				HttpMDTManager mdt = HttpMDTManager.connectWithDefault();
				HttpMDTInstanceManager manager = mdt.getInstanceManager();
				return FStream.from(manager.getInstanceAll())
//								.peek(System.out::println)
								.filterNot(inst -> inst.isRunning())
								.map(MDTInstance::getId)
								.iterator();
			}
			catch ( Exception e ) {
	            s_logger.warn("failed to get MDT instance ids", e);
	            return Collections.emptyIterator();
	        }
		}
	}

	public static class ElementReferenceCompletions implements Iterable<String> {
		@Override
		public Iterator<String> iterator() {
			try {
				HttpMDTManager mdt = HttpMDTManager.connectWithDefault();
				HttpMDTInstanceManager manager = mdt.getInstanceManager();
				return FStream.from(manager.getInstanceAll())
								.filter(inst -> inst.isRunning())
								.flatMap(this::listCandidates)
								.iterator();
			}
			catch ( Exception e ) {
	            s_logger.warn("failed to get MDT instance ids", e);
	            return Collections.emptyIterator();
	        }
		}
		
		public FStream<String> listCandidates(MDTInstance instance) {
			FStream<String> params
							= FStream.from(instance.getParameterServiceAll())
									.map(param -> String.format("param:%s:%s", instance.getId(), param.getId()))
									.concatWith("param:" + instance.getId() + ":*");
			
			FStream<String> opArgs
					= FStream.from(instance.getMDTOperationDescriptorAll())
							.flatMap(desc -> getInputOutputArgumentReferences(instance.getId(), desc));
			
			return FStream.concat(params, opArgs);
		}
		
		private FStream<String> getInputOutputArgumentReferences(String instId, MDTOperationDescriptor desc) {
			FStream<String> inArgs = FStream.from(desc.getInputArguments())
											.map(arg -> String.format("oparg:%s:%s:in:%s", instId, desc.getId(),
																							arg.getId()));
			FStream<String> outArgs = FStream.from(desc.getOutputArguments())
											.map(arg -> String.format("oparg:%s:%s:out:%s", instId, desc.getId(),
																							arg.getId()));
			return FStream.concat(inArgs, outArgs);
		}
	}
}
