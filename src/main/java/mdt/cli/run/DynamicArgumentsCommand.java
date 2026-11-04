package mdt.cli.run;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Unmatched;

import utils.Split;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;


/**
 * Command line에서 태스크의 입출력 인자를 받는 커맨드들의 공통 기반 클래스.
 * <p>
 * picocli가 인식하지 못한({@literal @}Unmatched) 옵션들을 태스크 인자로 해석한다.
 * 인자는 다음과 같은 형식으로 지정된다:
 * <ul>
 * 	<li>{@code --in.<name>=<expr>}: input 인자. 참조 표현식 또는 리터럴.</li>
 * 	<li>{@code --out.<name>=<ref-expr>}: output 인자. 참조 표현식만 허용된다.</li>
 * 	<li>{@code --inout.<name>=<ref-expr>}: input/output 인자. 참조 표현식만 허용된다.</li>
 * </ul>
 * 종류가 생략된 경우({@code --<name>=<expr>})는 input 인자로 간주한다.
 * <p>
 * 값을 별도 토큰으로 분리한 {@code --in.<name> <expr>} 형식도 지원하지만, 이 경우 값 토큰이
 * picocli의 위치 인자({@literal @}Parameters)로 소비될 수 있기 때문에 반드시 커맨드의 모든 위치 인자를
 * 지정한 뒤에 사용해야 한다. 값이 위치 인자로 소비된 것이 감지되면 오류가 발생한다.
 * 값이 {@code '-'}로 시작하는 경우({@code -3} 등의 음수)에는 {@code =} 형식을 사용해야 안전하다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
// picocli는 @Unmatched 필드를 @Command 클래스 안에서만 허용한다.
@Command
public abstract class DynamicArgumentsCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(DynamicArgumentsCommand.class);
	private static final String KIND_INPUT = "in";
	private static final String KIND_OUTPUT = "out";
	private static final String KIND_INOUTPUT = "inout";
	private static final Set<String> ARGUMENT_KINDS = Set.of(KIND_INPUT, KIND_OUTPUT, KIND_INOUTPUT);

	@Spec
	private CommandSpec m_spec;

	@Unmatched()
	private List<String> m_unmatchedArgs = Lists.newArrayList();

	protected DynamicArgumentsCommand() {
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
		getLogger().debug("unmatched options: {}", m_unmatchedArgs);

		// 값이 별도 토큰으로 분리된 '--in.<name> <value>' 형식에서는 값 토큰이 picocli의 위치 인자로
		// 소비되었을 수 있기 때문에, unmatched 토큰만으로는 값이 온전한지 알 수 없다.
		// 따라서 원본 command line을 기준으로 unmatched 토큰의 위치를 확인한다.
		List<String> originalArgs = getOriginalArgs();
		return parseUnmatchedOptions((originalArgs != null) ? originalArgs : m_unmatchedArgs, m_unmatchedArgs);
	}

	/**
	 * 원본 command line({@code args})에서 picocli가 인식하지 못한 토큰들({@code unmatcheds})을
	 * 태스크 인자 옵션으로 해석한다.
	 * <p>
	 * {@code unmatcheds}는 {@code args}의 부분 수열(subsequence)이기 때문에, 두 리스트를 앞에서부터
	 * 나란히 훑으면 각 unmatched 토큰이 원본 command line의 어느 위치에 있었는지 알 수 있다.
	 * 이를 이용하여 옵션 값으로 사용되어야 할 토큰이 picocli의 위치 인자로 소비된 경우를 검출한다.
	 *
	 * @param args			원본 command line 인자 리스트.
	 * @param unmatcheds	picocli가 인식하지 못한 토큰 리스트.
	 * @return	해석된 태스크 인자 옵션 리스트.
	 * @throws IllegalArgumentException	옵션 형식이 잘못되었거나, 옵션 값이 위치 인자로 소비된 경우.
	 */
	private static List<UnmatchedOption> parseUnmatchedOptions(List<String> args, List<String> unmatcheds) {
		List<UnmatchedOption> unmatchedOptions = Lists.newArrayList();

		int uidx = 0;	// unmatcheds에서 아직 처리되지 않은 첫번째 토큰의 인덱스
		for ( int i = 0; i < args.size() && uidx < unmatcheds.size(); ++i ) {
			if ( !args.get(i).equals(unmatcheds.get(uidx)) ) {
				// picocli가 옵션 또는 위치 인자로 이미 소비한 토큰.
				continue;
			}

			String optToken = unmatcheds.get(uidx++);
			String optName = trimHeadingDashes(optToken);

			// '--<kind>.<name>=<value>' 형식: 값이 옵션 토큰에 포함되어 있다.
			Split assign = Split.split(optName, "=");
			if ( assign.tail().isPresent() ) {
				unmatchedOptions.add(toUnmatchedOption(assign.head(), assign.tail().get()));
				continue;
			}

			// '--<kind>.<name> <value>' 형식: 뒤따르는 토큰이 값이다.
			String value = null;
			if ( i+1 < args.size() && !looksLikeOptionName(args.get(i+1)) ) {
				String next = args.get(i+1);
				if ( uidx >= unmatcheds.size() || !next.equals(unmatcheds.get(uidx)) ) {
					// 값 토큰을 picocli가 위치 인자로 가져간 경우.
					throw new IllegalArgumentException(
							String.format("argument value '%s' was consumed as a positional parameter: opt=%s. "
											+ "Specify dynamic arguments after all positional parameters, "
											+ "or use the '%s=%s' form.", next, optToken, optToken, next));
				}
				value = next;
				++uidx;
				++i;
			}
			unmatchedOptions.add(toUnmatchedOption(optName, value));
		}

		return unmatchedOptions;
	}

	/**
	 * 옵션 이름을 인자 종류와 인자 이름으로 분리하여 {@link UnmatchedOption}을 생성한다.
	 * <p>
	 * 선두 부분이 지원되는 인자 종류인 경우에만 종류로 해석하기 때문에, 인자 이름 자체에
	 * {@code '.'}이 포함되어도 무방하다. (예: {@code --a.b}는 {@code a.b}라는 이름의 input 인자)
	 *
	 * @param optName	'-' 접두어가 제거된 옵션 이름.
	 * @param value		옵션 값. 지정되지 않은 경우는 {@code null}.
	 * @return	생성된 {@link UnmatchedOption} 객체.
	 */
	private static UnmatchedOption toUnmatchedOption(String optName, String value) {
		Split split = Split.split(optName, ".");
		if ( split.tail().isPresent() && ARGUMENT_KINDS.contains(split.head().toLowerCase()) ) {
			return new UnmatchedOption(split.head().toLowerCase(), split.tail().get(), value);
		}

		// 종류가 생략된 경우는 input 인자로 간주한다.
		return new UnmatchedOption(KIND_INPUT, optName, value);
	}

	/**
	 * 주어진 토큰이 옵션 이름 형식인지 여부를 반환한다.
	 * <p>
	 * {@code '-'}로 시작하더라도 뒤에 숫자가 오는 경우({@code -3}, {@code -0.5} 등)는 음수 값으로
	 * 간주하여 옵션 이름으로 보지 않는다.
	 *
	 * @param token	검사 대상 토큰.
	 * @return	옵션 이름 형식인 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 */
	private static boolean looksLikeOptionName(String token) {
		if ( !token.startsWith("-") ) {
			return false;
		}
		return !(token.length() > 1 && Character.isDigit(token.charAt(1)));
	}

	private List<String> getOriginalArgs() {
		if ( m_spec == null ) {
			return null;
		}

		CommandLine cmdLine = m_spec.commandLine();
		if ( cmdLine == null ) {
			return null;
		}

		ParseResult parseResult = cmdLine.getParseResult();
		return ( parseResult != null ) ? parseResult.originalArgs() : null;
	}

	/**
	 * Command line에서 수집된 태스크의 input/output/inoutput 인자 명세.
	 */
	public static class TaskCommandArguments {
		// 명령줄에 지정한 순서를 유지한다.
		private final Map<String,String> m_inputs = Maps.newLinkedHashMap();
		private final Map<String,String> m_outputs = Maps.newLinkedHashMap();
		private final Map<String,String> m_inoutputs = Maps.newLinkedHashMap();

		public Map<String,String> getInputs() {
			return m_inputs;
		}

		public void addInput(String id, String argSpec) {
			m_inputs.put(id, argSpec);
		}

		public Map<String,String> getOutputs() {
			return m_outputs;
		}

		public void addOutput(String id, String argSpec) {
			m_outputs.put(id, argSpec);
		}

		public Map<String,String> getInoutputs() {
			return m_inoutputs;
		}

		public void addInoutput(String id, String argSpec) {
			m_inoutputs.put(id, argSpec);
		}
	}

	/**
	 * Command line에서 지정된 옵션을 파싱하여 태스크의 input/output/inoutput 인자를 수집한다.
	 * <p>
	 * 인자는 {@code --in.<name>}, {@code --out.<name>}, {@code --inout.<name>} 형식이며, 종류를 생략한
	 * {@code --<name>}은 입력 인자로 본다. 같은 종류·이름의 인자를 두 번 지정하면 사용법 오류다.
	 *
	 * @return	수집된 태스크 인자 명세.
	 * @throws IOException				인자 값을 해석하는 과정에서 오류가 발생한 경우.
	 * @throws CommandLine.ParameterException	인자 값이 지정되지 않았거나 같은 인자가 두 번 지정된 경우.
	 * @throws IllegalArgumentException	인자 형식이 잘못된 경우.
	 */
	protected TaskCommandArguments loadTaskArgumentsFromCommandLine()
		throws IOException {
		TaskCommandArguments args = new TaskCommandArguments();
		List<UnmatchedOption> unmatchedOptions = collectUnmatchedOptions();
		for ( UnmatchedOption unmatchedOpt: unmatchedOptions ) {
			String name = unmatchedOpt.getName();
			String argSpec = unmatchedOpt.getValue();
			if ( argSpec == null ) {
				throw usageError("argument value is not specified: arg=" + name);
			}

			// 인자 종류는 toUnmatchedOption()에서 ARGUMENT_KINDS 중 하나로 결정된다.
			String kind = unmatchedOpt.getType();
			Map<String,String> existing = switch ( kind ) {
				case KIND_INPUT -> args.getInputs();
				case KIND_OUTPUT -> args.getOutputs();
				case KIND_INOUTPUT -> args.getInoutputs();
				default -> throw new AssertionError("unexpected argument kind: " + kind);
			};
			if ( existing.containsKey(name) ) {
				throw usageError(String.format("duplicate %s argument: --%s.%s", kind, kind, name));
			}
			switch ( kind ) {
				case KIND_INPUT:
					args.addInput(name, argSpec);
					break;
				case KIND_OUTPUT:
					args.addOutput(name, argSpec);
					break;
				case KIND_INOUTPUT:
					args.addInoutput(name, argSpec);
					break;
				default:
					throw new AssertionError("unexpected argument kind: " + kind);
			}
			getLogger().debug("add {} argument variable[{}]", kind, name);
		}

		return args;
	}

	/**
	 * 명령행에서 지정된 태스크 인자의 이름이 모두 대상 연산에 선언된 변수 이름인지 확인한다.
	 * <p>
	 * 선언되지 않은 이름으로 지정된 인자는 대상 연산의 변수와 매칭되지 않아 조용히 무시되기 때문에,
	 * 인자 이름의 오타를 사용자가 알아채기 어렵다. 이를 방지하기 위해 명시적으로 오류를 발생시킨다.
	 *
	 * @param kind		인자 종류. 오류 메시지에만 사용된다. (예: {@code "input"})
	 * @param cmdArgs	명령행에서 지정된 인자 명세. (인자 이름 -&gt; 인자 표현식)
	 * @param varNames	대상 연산에 선언된 변수 이름 집합.
	 * @throws CommandLine.ParameterException	선언되지 않은 이름의 인자가 지정된 경우.
	 */
	protected void checkArgumentNames(String kind, Map<String,String> cmdArgs, Set<String> varNames) {
		List<String> unknowns = FStream.from(cmdArgs.keySet())
										.filterNot(varNames::contains)
										.sort()
										.toList();
		if ( !unknowns.isEmpty() ) {
			throw usageError(
					String.format("unknown %s argument(s): %s (declared %s variables: %s)",
									kind, unknowns, kind, varNames));
		}
	}

	private static String trimHeadingDashes(String optName) {
		if ( optName.startsWith("--") ) {
			return optName.substring(2);
		}
		else if ( optName.startsWith("-") ) {
			return optName.substring(1);
		}
		else {
			throw new IllegalArgumentException("unexpected command line argument: " + optName);
		}
	}
}
