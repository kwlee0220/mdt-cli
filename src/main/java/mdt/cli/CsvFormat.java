package mdt.cli;

import java.util.Arrays;

import utils.stream.FStream;


/**
 * CLI의 CSV(구분자 구분 텍스트) 출력에서 한 줄을 만드는 유틸리티.
 * <p>
 * 값에 구분자, 큰따옴표, 줄바꿈이 들어 있으면 RFC 4180 방식으로 큰따옴표로 감싸고, 값 안의 큰따옴표는 두 번 적는다.
 * 그 밖의 값은 그대로 쓴다. {@code null}은 빈 값으로 쓴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class CsvFormat {
	private CsvFormat() {
		throw new AssertionError("Should not be called: class=" + CsvFormat.class.getName());
	}

	/**
	 * 값들을 구분자로 이어 CSV 한 줄을 만든다.
	 *
	 * @param delim		필드 구분자.
	 * @param values	필드 값들. 각 값은 {@link Object#toString()}으로 문자열이 된다.
	 * @return	CSV 한 줄(줄바꿈 문자는 붙이지 않는다).
	 */
	public static String toLine(String delim, Iterable<?> values) {
		return FStream.from(values)
						.map(v -> quote(v, delim))
						.join(delim);
	}

	/**
	 * 값들을 구분자로 이어 CSV 한 줄을 만든다.
	 *
	 * @param delim		필드 구분자.
	 * @param values	필드 값들.
	 * @return	CSV 한 줄(줄바꿈 문자는 붙이지 않는다).
	 */
	public static String toLine(String delim, Object... values) {
		return toLine(delim, Arrays.asList(values));
	}

	/**
	 * 필드 값 하나를 CSV 표기로 바꾼다.
	 *
	 * @param value	필드 값. {@code null}이면 빈 문자열.
	 * @param delim	필드 구분자.
	 * @return	필요하면 큰따옴표로 감싼 값.
	 */
	public static String quote(Object value, String delim) {
		String str = (value != null) ? value.toString() : "";
		boolean needsQuote = str.contains(delim) || str.indexOf('"') >= 0
							|| str.indexOf('\n') >= 0 || str.indexOf('\r') >= 0;
		return needsQuote ? "\"" + str.replace("\"", "\"\"") + "\"" : str;
	}
}
