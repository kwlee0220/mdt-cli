package mdt.cli.list;

import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import utils.UnitUtils;
import utils.http.RESTfulIOException;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.PeriodicRefreshingConsole;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.workflow.WorkflowManager;
import mdt.workflow.WorkflowModel;

/**
 * MDT Manager에 등록된 워크플로우 모델 목록을 출력하는 CLI 명령.
 * <p>
 * 출력은 옵션 조합으로 다음 4가지 형태가 가능하다.
 * <ul>
 *   <li>short / 텍스트 — {@code seq, id, taskCount}</li>
 *   <li>short / 테이블 — 위와 동일 컬럼을 {@link Table}로 표시</li>
 *   <li>long / 텍스트 — {@code seq, id, name, taskCount}</li>
 *   <li>long / 테이블</li>
 * </ul>
 * 추가로 {@code --glob}으로 ID 기준 필터링, {@code --repeat}로 주기적 화면 갱신
 * ({@link PeriodicRefreshingConsole})을 지원한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "wfmodels",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all MDT Workflow models."
)
public class ListWorkflowModelAllCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListWorkflowModelAllCommand.class);

	/** {@code -t/--table}: 출력 형식을 텍스트 대신 테이블로 변경. */
	@Option(names={"--table", "-t"}, description="display instances in a table format.")
	private boolean m_tableFormat = false;

	/** {@code -g/--glob}: 모델 ID에 적용할 glob 패턴. 지정 시 매칭되는 모델만 출력된다. */
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern to filter workflows.")
	private String m_glob = null;

	/** {@code -l/--long}: name 컬럼을 포함한 상세 정보를 함께 출력. */
	@Option(names={"--long", "-l"}, description="show detailed information.")
	private boolean m_long = false;

	/** {@code -d/--delimiter}: 텍스트 출력 시 컬럼 구분자. */
	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'csv' output only)")
	private String m_delimiter = ListCommands.DELIM;

	/** {@code -r/--repeat}: 지정 시 해당 주기({@link UnitUtils#parseDuration})로 화면을 갱신한다. */
	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\"")
	private String m_repeat = null;

	/** {@code -v}: {@code --repeat} 사용 시 주기별 소요 시간을 함께 출력한다. */
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	/**
	 * 명령줄 진입점. 인자를 파싱하여 이 명령을 실행한다.
	 *
	 * @param args 명령줄 인자.
	 * @throws Exception 실행 중 발생한 임의의 예외.
	 */
	public static final void main(String... args) throws Exception {
		main(new ListWorkflowModelAllCommand(), args);
	}

	public ListWorkflowModelAllCommand() {
		setLogger(s_logger);
	}

	/**
	 * 명령을 실행한다.
	 * <p>
	 * {@code --repeat}이 지정되지 않은 경우 한 번만 출력하고 종료한다. 지정된 경우
	 * {@link PeriodicRefreshingConsole}을 통해 주기적으로 출력을 갱신하며, 사용자 입력
	 * ({@code q}/{@code Q}/ESC)으로 종료된다. 주기 내 예외는 흡수되어 한 줄 오류 메시지로
	 * 표시되고 다음 주기로 진행된다.
	 *
	 * @param mdt MDT 매니저.
	 * @throws Exception 실행 중 발생한 임의의 예외.
	 */
	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfManager = mdt.getWorkflowManager();

		if ( m_repeat == null ) {
			try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
				printOutput(wfManager, pw);
			}
			return;
		}
		else {
			Duration repeatInterval = UnitUtils.parseDuration(m_repeat);
			PeriodicRefreshingConsole pwriter = new PeriodicRefreshingConsole(repeatInterval) {
				@Override
				protected void print(PrintWriter pw) {
					try {
						printOutput(wfManager, pw);
					}
					catch ( Exception e ) {
						pw.println("failed to list Workflow models: cause=" + e);
					}
				}
			};
			pwriter.setVerbose(m_verbose);
			pwriter.run();
		}
	}
	
	/**
	 * 워크플로우 모델 목록을 조회하여 옵션 조합에 맞는 포맷으로 한 번 출력한다.
	 * <p>
	 * {@link ResourceNotFoundException}과 일반 {@link RESTfulIOException}은 한 줄 오류 메시지로
	 * 출력하고 정상 반환한다. 단, 통신 도중 인터럽트되어 발생한 RESTfulIOException
	 * (원인이 {@link InterruptedIOException})은 {@link InterruptedException}으로 다시 던져
	 * 호출자(특히 {@code --repeat} 루프)가 종료할 수 있게 한다.
	 *
	 * @param wfManager 워크플로우 매니저.
	 * @param pw        출력 writer.
	 * @throws InterruptedException 통신이 인터럽트로 중단된 경우.
	 */
	private void printOutput(WorkflowManager wfManager, PrintWriter pw) throws InterruptedException {
		try {
			List<WorkflowModel> wfModelList = listWorkflowModels(wfManager);
			if ( m_long ) {
				if ( m_tableFormat ) {
					printModelsLongTable(wfModelList, pw);
				}
				else {
			    	printModelsLongList(wfModelList, pw);
				}
			}
			else {
				if ( m_tableFormat ) {
					printModelsShortTable(wfModelList, pw);
				}
				else {
					printModelsShortList(wfModelList, pw);
				}
			}
		}
		catch ( ResourceNotFoundException e ) {
			pw.println("fails to list MDTWorkflowModels: " + e.getCause());
		}
		catch ( RESTfulIOException e ) {
			Throwable cause = e.getCause();
			if ( cause instanceof InterruptedIOException ) {
				throw new InterruptedException("interrupted while listing MDTWorkflowModels");
			}
			else {
				pw.println("fails to list MDTWorkflowModels: " + e.getCause());
			}
		}
	}
	
	/**
	 * 매니저로부터 워크플로우 모델 전체를 가져오고, {@code --glob}이 지정된 경우 ID에 대해
	 * 패턴 필터링을 적용한 결과를 반환한다.
	 *
	 * @param wfMgr 워크플로우 매니저.
	 * @return      필터링된 모델 리스트.
	 */
	private List<WorkflowModel> listWorkflowModels(WorkflowManager wfMgr) {
		if ( m_glob != null ) {
			String pattern = "glob:" + m_glob;
	        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
	        
	        return FStream.from(wfMgr.getWorkflowModelAll())
					        .filter(wf -> matcher.matches(Paths.get(wf.getId())))
					        .toList();
		}
		else {
			return wfMgr.getWorkflowModelAll();
		}
	}
	
	private void printModelsShortList(List<WorkflowModel> wfModelList, PrintWriter pw) {
		int seqNo = 1;
		for ( WorkflowModel wfModel : wfModelList ) {
			pw.append(""+seqNo).append(m_delimiter);
			pw.append(wfModel.getId()).append(m_delimiter);
			pw.append(""+wfModel.getTaskDescriptors().size());
			pw.println();
			++seqNo;
		}
	}
	
	private void printModelsShortTable(List<WorkflowModel> wfModelList, PrintWriter pw) {
		Table table = new Table(3);
		table.addCell(" # ");
		table.addCell(" ID ");
		table.addCell(" COUNT ");
		
		int seqNo = 1;
		for ( WorkflowModel wfModel : wfModelList ) {
			table.addCell(String.format("%3d", seqNo));
			table.addCell(wfModel.getId());
			table.addCell(String.format("%6d", wfModel.getTaskDescriptors().size()));
			++seqNo;
		}
		pw.println(table.render());
	}
	
	private void printModelsLongList(List<WorkflowModel> wfModelList, PrintWriter pw) {
		int seqNo = 1;
		for ( WorkflowModel wfModel : wfModelList ) {
			pw.append(""+seqNo).append(m_delimiter);
			pw.append(wfModel.getId()).append(m_delimiter);
			pw.append(wfModel.getName()).append(m_delimiter);
			pw.append(""+wfModel.getTaskDescriptors().size());
			pw.println();
			++seqNo;
		}
	}
	
	private void printModelsLongTable(List<WorkflowModel> wfModelList, PrintWriter pw) {
		Table table = new Table(4);
		table.addCell(" # ");
		table.addCell(" ID ");
		table.addCell(" NAME ");
		table.addCell(" COUNT ");
		
		int seqNo = 1;
		for ( WorkflowModel wfModel : wfModelList ) {
			table.addCell(String.format("%3d", seqNo));
			table.addCell(wfModel.getId());
			table.addCell(wfModel.getName());
			table.addCell(String.format("%6d", wfModel.getTaskDescriptors().size()));
			++seqNo;
		}
		pw.println(table.render());
	}
}
