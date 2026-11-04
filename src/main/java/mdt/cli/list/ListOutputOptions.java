package mdt.cli.list;

import picocli.CommandLine.Option;


/**
 * 목록 명령의 출력 형식 옵션({@code --output csv|table}, 줄임 {@code --table})을 담은 picocli mixin.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ListOutputOptions {
	/** 목록 출력 형식. */
	public enum ListOutputType { csv, table };

	@Option(names={"--output", "-o"}, paramLabel="type",
			description="output type (candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE})")
	private ListOutputType m_output = ListOutputType.csv;

	@Option(names={"--table", "-t"}, description="same as '--output table'")
	private boolean m_table = false;

	/**
	 * 표 형식으로 출력할지 여부를 반환한다.
	 *
	 * @return	{@code --output table} 또는 {@code --table}이 지정되었으면 {@code true}.
	 */
	public boolean isTable() {
		return m_table || m_output == ListOutputType.table;
	}
}
