package mdt.cli.run;

import java.time.Duration;

import picocli.CommandLine.Option;


/**
 * 원격 연산 호출 커맨드들이 공통으로 제공하는 실행 옵션 mixin.
 * <p>
 * 연산 실행 상태 확인 주기({@code --poll}), 수행 제한 시간({@code --timeout}), 결과 출력 여부
 * ({@code --showResult})를 정의한다. picocli {@literal @}Mixin으로 커맨드에 포함시켜 사용한다.
 * <p>
 * {@link Duration} 타입 변환은 {@link utils.Picoclies.DurationConverter}가 담당하기 때문에
 * 잘못된 값이 지정된 경우 커맨드 수행 이전 파싱 단계에서 사용법과 함께 오류가 보고된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OperationRunOptions {
	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

	// 기본값을 picocli의 'defaultValue'가 아닌 필드 초기값으로 지정하는 이유는, 'defaultValue'를
	// 사용하는 경우 옵션이 지정되지 않았을 때에도 Duration 변환기가 등록되어 있어야 하기 때문이다.
	@Option(names={"--poll"}, paramLabel="duration",
			description="Status polling interval (e.g. \"1s\", \"500ms\"). default: 1s")
	private Duration m_pollInterval = DEFAULT_POLL_INTERVAL;

	@Option(names={"--timeout"}, paramLabel="duration",
			description="Operation execution timeout (e.g. \"30s\", \"1m\"). default: no timeout")
	private Duration m_timeout = null;

	@Option(names={"--showResult", "--showResults"}, description="show output/inoutput operation variables")
	private boolean m_showResult = false;

	/**
	 * 연산 실행 상태 확인 주기를 반환한다.
	 *
	 * @return	실행 상태 확인 주기. 지정되지 않은 경우는 기본값(1초).
	 */
	public Duration getPollInterval() {
		return m_pollInterval;
	}

	/**
	 * 연산 수행 제한 시간을 반환한다.
	 *
	 * @return	수행 제한 시간. 지정되지 않은 경우는 {@code null}.
	 */
	public Duration getTimeout() {
		return m_timeout;
	}

	/**
	 * 연산의 출력/입출력 변수 값을 표준 출력으로 내보낼 지 여부를 반환한다.
	 *
	 * @return	출력하는 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 */
	public boolean getShowResult() {
		return m_showResult;
	}
}
