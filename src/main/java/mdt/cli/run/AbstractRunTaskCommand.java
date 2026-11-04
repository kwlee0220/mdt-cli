package mdt.cli.run;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Mixin;

import utils.Throwables;

import mdt.aas.DataType;
import mdt.model.MDTManager;
import mdt.model.MDTModelSerDe;
import mdt.model.expr.MDTParserException;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.ElementValue;
import mdt.model.sm.value.ElementValues;
import mdt.model.sm.value.PropertyValue;
import mdt.task.AbstractOperationTask;
import mdt.task.TaskArgument;


/**
 * AAS 연산 호출 기반 태스크({@link AbstractOperationTask})를 실행하는 커맨드들의 공통 기반 클래스.
 * <p>
 * poll/timeout/showResult 옵션 처리, 태스크 실행, 결과 출력, 수행 시간 로깅을 담당한다.
 * 하위 클래스는 {@link #newTask(MDTInstanceManager, TaskCommandArguments, Duration, Duration)}를
 * 구현하여 실행할 태스크를 결정하고, {@link #getTargetString()}으로 로그에 표시할 대상 문자열을 제공한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractRunTaskCommand extends DynamicArgumentsCommand {
	@Mixin private OperationRunOptions m_runOptions;

	/**
	 * Command line에서 수집된 인자로 실행할 태스크를 생성한다.
	 *
	 * @param manager		MDT 인스턴스 관리자 객체.
	 * @param cmdArgs		command line에서 수집된 태스크 인자 명세.
	 * @param pollInterval	실행 상태 확인 주기.
	 * @param timeout		태스크 수행 제한 시간. 지정되지 않은 경우는 {@code null}.
	 * @return	실행할 태스크 객체.
	 */
	protected abstract AbstractOperationTask newTask(MDTInstanceManager manager, TaskCommandArguments cmdArgs,
														Duration pollInterval, Duration timeout);

	/**
	 * 수행 시간 로그에 표시할 태스크 대상 문자열을 반환한다.
	 *
	 * @return	태스크 대상 문자열.
	 */
	protected abstract String getTargetString();

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		Instant started = Instant.now();

		TaskCommandArguments taskArgs = loadTaskArgumentsFromCommandLine();

		AbstractOperationTask task = newTask(manager, taskArgs, m_runOptions.getPollInterval(),
											m_runOptions.getTimeout());

		Map<String,SubmodelElement> outputs = task.run(manager);
		if ( m_runOptions.isShowResult() ) {
			for ( Map.Entry<String,SubmodelElement> e: outputs.entrySet() ) {
				ElementValue value = ElementValues.getValue(e.getValue());
				out().printf("%s: %s%n", e.getKey(), (value != null) ? value.toDisplayString() : "(none)");
			}
		}

		Duration elapsed = Duration.between(started, Instant.now());
		getLogger().info("{}, elapsedTime={}", getTargetString(), elapsed);
	}

	/**
	 * 명령줄의 입력 인자 값을 {@link TaskArgument}로 바꾼다.
	 * <p>
	 * 값이 요소 참조 표현식이면 참조 인자가 되고, 그렇지 않으면 대상 변수의 타입에 맞는 리터럴로 해석한다
	 * ({@link #parseLiteralArgument(SubmodelElement, String)}).
	 *
	 * @param argSme	값을 해석할 기준이 되는 인자 변수의 {@link SubmodelElement}.
	 * @param argSpec	명령줄에 주어진 인자 값.
	 * @return	변환된 태스크 인자.
	 */
	protected static TaskArgument toInputTaskArgument(SubmodelElement argSme, String argSpec) {
		try {
			MDTElementReference ref = ElementReferences.parseExpr(argSpec);
			return TaskArgument.reference(ref);
		}
		catch ( MDTParserException notReference ) {
			// 참조 표현식이 아닌 경우는 인자 변수의 타입에 맞는 리터럴로 해석한다.
			try {
				return parseLiteralArgument(argSme, argSpec);
			}
			catch ( IOException e ) {
				return Throwables.sneakyThrow(e);
			}
		}
	}

	/**
	 * Command line에서 지정된 리터럴 인자 값을 대상 연산 변수의 타입에 맞게 해석하여 태스크 인자를 생성한다.
	 * <p>
	 * 리터럴 값은 기본적으로 JSON으로 해석되지만, 유효한 JSON이 아니면서 대상 변수가 Property인 경우는
	 * 따옴표가 생략된 값 리터럴로 간주하여 변수의 데이터 타입으로 직접 해석한다. 즉,
	 * {@code --in.name=hello}는 {@code --in.name="hello"}와 동일하게 취급된다.
	 *
	 * @param opvSme	값을 해석할 기준이 되는 연산 변수의 {@link SubmodelElement}.
	 * @param literal	command line에서 지정된 리터럴 값.
	 * @return	생성된 태스크 인자.
	 * @throws IOException	리터럴 값이 대상 연산 변수의 타입과 맞지 않는 경우.
	 */
	protected static TaskArgument parseLiteralArgument(SubmodelElement opvSme, String literal) throws IOException {
		ElementValue proto = ElementValues.getValue(opvSme);
		try {
			JsonNode valueNode = MDTModelSerDe.getJsonMapper().readTree(literal);
			return TaskArgument.literal(ElementValues.parseValueJsonNode(valueNode, proto));
		}
		catch ( JsonProcessingException e ) {
			if ( !(proto instanceof PropertyValue<?> propValue) ) {
				throw e;
			}

			// 따옴표가 생략된 값 리터럴을 대상 변수의 데이터 타입으로 직접 해석한다.
			// 타입에 맞지 않는 값은 여기서 오류가 발생한다.
			DataType<?> dataType = propValue.getDataType();
			try {
				return TaskArgument.literal(ElementValues.fromValueObject(dataType.parseValueString(literal), proto));
			}
			catch ( RuntimeException cause ) {
				String msg = String.format("invalid %s literal: '%s', variable=%s",
											dataType.getName(), literal, opvSme.getIdShort());
				throw new IllegalArgumentException(msg, cause);
			}
		}
	}
}
