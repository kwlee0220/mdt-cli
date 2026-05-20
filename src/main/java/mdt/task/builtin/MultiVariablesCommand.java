package mdt.task.builtin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import utils.Split;

import mdt.cli.AbstractMDTCommand;
import mdt.workflow.model.ArgumentSpec;
import mdt.workflow.model.ArgumentSpec.ReferenceArgumentSpec;

import picocli.CommandLine.Command;
import picocli.CommandLine.Unmatched;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(name = "test")
public abstract class MultiVariablesCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(MultiVariablesCommand.class);
	
	@Unmatched()
	private List<String> m_unmatcheds = Lists.newArrayList();
	
	protected MultiVariablesCommand() {
		setLogger(s_logger);
	}
	
	@Getter
	@AllArgsConstructor
	@Accessors(prefix = "m_")
	public static class UnmatchedOption {
		private final String m_type;
		private final String m_name;
		private final String m_value;
		
		@Override
		public String toString() {
			return String.format("%s.%s=%s", m_type, m_name, m_value);
		}
	}
	
	protected List<UnmatchedOption> collectUnmatchedOptions() {
		s_logger.debug("unmatched options: {}", m_unmatcheds);
		
		List<UnmatchedOption> unmatchedOptions = Lists.newArrayList();
		List<String> remains = Lists.newArrayList(m_unmatcheds);
		while ( remains.size() > 0 ) {
			String optName = remains.remove(0);
			
			optName = trimHeadingDashes(optName);
			
			var split = Split.split(optName, ".");
			String type = split.tail().isPresent() ? split.head() : "in";
			String name = split.tail().orElse(optName);
			if ( remains.size() == 0 || remains.get(0).startsWith("-") ) {
				// 옵션의 value가 지정되지 않은 경우
				unmatchedOptions.add(new UnmatchedOption(type, name, null));
			}
			else {
				unmatchedOptions.add(new UnmatchedOption(type, name, remains.remove(0)));
			}
		}
		
		return unmatchedOptions;
	}
	
	public static class TaskArgumentsDescriptor {
		private final Map<String,ArgumentSpec> m_inputs = Maps.newHashMap();
		private final Map<String,ReferenceArgumentSpec> m_outputs = Maps.newHashMap();
		private final Map<String,ReferenceArgumentSpec> m_inoutputs = Maps.newHashMap();
		private final Map<String,String> m_options = Maps.newHashMap();

		public Map<String,ArgumentSpec> getInputs() {
			return m_inputs;
		}

		public void addInput(String id, ArgumentSpec argSpec) {
			m_inputs.put(id, argSpec);
		}

		public Map<String,ReferenceArgumentSpec> getOutputs() {
			return m_outputs;
		}

		public void addOutput(String id, ReferenceArgumentSpec argSpec) {
			m_outputs.put(id, argSpec);
		}

		public Map<String,ReferenceArgumentSpec> getInoutputs() {
			return m_inoutputs;
		}

		public void addInoutput(String id, ReferenceArgumentSpec argSpec) {
			m_inoutputs.put(id, argSpec);
		}
		
		public Map<String,String> getOptions() {
			return m_options;
		}

		public void addOption(String name, String value) {
			m_options.put(name, value);
		}
	}
	
	protected TaskArgumentsDescriptor loadTaskArgumentsFromCommandLine()
		throws IOException {
		// Command line에서 지정된 옵션을 파싱하여 input/output parameter를 추출한다.
		// 이때, input/output parameter 관련 정보들은 unmatcheds에 포함되어 있다.
		// Input/output parameter는 다음과 같은 형식으로 지정된다.
		//   --in.<parameter-name> <element-reference> (input parameter의 경우)
		//   --out.<parameter-name> <element-reference> (output parameter의 경우)
		//   --inout.<parameter-name> <element-reference> (input/output parameter의 경우)
		//
		TaskArgumentsDescriptor varsDesc = new TaskArgumentsDescriptor();
		List<UnmatchedOption> unmatchedOptions = collectUnmatchedOptions();
		for ( UnmatchedOption unmatchedOpt: unmatchedOptions ) {
			String name = unmatchedOpt.getName();
			String value = unmatchedOpt.getValue();
			
			if ( value == null ) {
				throw new IllegalArgumentException("argument is not specified: arg=" + name);
			}
			
			ArgumentSpec argSpec = ArgumentSpec.parseArgumentSpec(unmatchedOpt.getValue());
			
			String kind = unmatchedOpt.getType();
			if ( kind == null ) {
				throw new IllegalArgumentException("unexpected option: " + unmatchedOpt);
			}
			switch ( kind.toLowerCase() ) {
				case "in":
					varsDesc.addInput(name, argSpec);
					getLogger().debug("add input parameter variable[{}]", name);
					break;
				case "out":
					Preconditions.checkArgument(argSpec instanceof ReferenceArgumentSpec,
												"output parameter must be reference argument spec: arg=" + name);
					varsDesc.addOutput(name, (ReferenceArgumentSpec)argSpec);
					getLogger().debug("add output parameter variable[{}]", name);
					break;
				case "inout":
					Preconditions.checkArgument(argSpec instanceof ReferenceArgumentSpec,
												"inoutput parameter must be reference argument spec: arg=" + name);
					varsDesc.addInoutput(name, (ReferenceArgumentSpec)argSpec);
					getLogger().debug("add inoutput parameter variable[{}]", name);
					break;
				case "opt":
				case "opton":
					varsDesc.addOption(unmatchedOpt.getName(), unmatchedOpt.getValue());
					getLogger().debug("add inoutput parameter variable[{}]", name);
					break;
				default:
					throw new AssertionError("invalid kind: " + kind);
			}
		}
		
		return varsDesc;
	}
	
	private static String trimHeadingDashes(String optName) {
		if ( optName.startsWith("--") ) {
			return optName.substring(2);
		}
		else if ( optName.startsWith("-") ) {
			return optName.substring(1);
		}
		else {
			throw new IllegalArgumentException("Invalid option name: " + optName);
		}
	}
}
