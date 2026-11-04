package mdt.cli.list;

import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;

import org.nocrala.tools.texttablefmt.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.http.RESTfulIOException;
import utils.stream.FStream;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.CsvFormat;
import mdt.cli.RepeatOptions;
import mdt.model.MDTManager;
import mdt.workflow.Workflow;
import mdt.workflow.WorkflowManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "workflows",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "List all MDT Workflow instances."
)
public class ListWorkflowsCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListWorkflowsCommand.class);
	
	@Mixin private ListOutputOptions m_outputOpts;
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern to filter workflows.")
	private String m_glob = null;

	@Option(names={"--long", "-l"}, description="show detailed information.")
	private boolean m_long = false;

	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'csv' output only)")
	private String m_delimiter = ListCommands.DELIM;

	@Mixin private RepeatOptions m_repeatOpts;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new ListWorkflowsCommand(), args);
	}
	
	public ListWorkflowsCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		WorkflowManager wfManager = mdt.getWorkflowManager();

		m_repeatOpts.run(pw -> printOutput(wfManager, pw), "list Workflow instances", m_verbose);
	}
	
	private void printOutput(WorkflowManager wfManager, PrintWriter pw) throws InterruptedException {
        try {
			List<Workflow> wfList = listWorkflows(wfManager);
			if ( m_long ) {
				if ( m_outputOpts.isTable() ) {
					printInstancesLongTable(wfList, pw);
				}
				else {
			    	printInstancesLongList(wfList, pw);
				}
			}
			else {
				if ( m_outputOpts.isTable() ) {
					printInstancesShortTable(wfList, pw);
				}
				else {
					printInstancesShortList(wfList, pw);
				}
			}
		}
		catch ( RESTfulIOException e ) {
			// 반복 출력 중 종료 키로 대기가 인터럽트된 경우는 반복을 멈추도록 InterruptedException으로 바꾼다.
			// 그 밖의 오류는 호출자(RepeatOptions)가 처리한다(단발 출력은 오류 종료, 반복 출력은 화면에 표시).
			if ( e.getCause() instanceof InterruptedIOException ) {
				Thread.currentThread().interrupt();
				throw new InterruptedException("interrupted while listing MDTWorkflows");
			}
			throw e;
		}
	}
	
	private List<Workflow> listWorkflows(WorkflowManager wfMgr) {
		if ( m_glob != null ) {
			String pattern = "glob:" + m_glob;
	        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
	        
	        return FStream.from(wfMgr.getWorkflowAll())
					        .filter(wf -> matcher.matches(Paths.get(wf.getName())))
					        .toList();
		}
		else {
			return wfMgr.getWorkflowAll();
		}
	}
	
	private void printInstancesShortList(List<Workflow> wfList, PrintWriter pw) {
		int seqNo = 1;
		for ( Workflow workflow : wfList ) {
			pw.println(CsvFormat.toLine(m_delimiter, seqNo, workflow.getName(), workflow.getModelId(),
										workflow.getTasks().size(), workflow.getStatus()));
			++seqNo;
		}
	}
	
	private void printInstancesShortTable(List<Workflow> wfList, PrintWriter pw) {
		Table table = new Table(5);
		table.setColumnWidth(1, 15, 50);
		table.setColumnWidth(4, 9, 9);
		table.addCell(" # ");
		table.addCell(" NAME ");
		table.addCell(" MODEL ");
		table.addCell(" COUNT ");
		table.addCell(" STATUS ");
		
		int seqNo = 1;
		for ( Workflow workflow : wfList ) {
			table.addCell(String.format("%3d", seqNo));
			table.addCell(workflow.getName());
			table.addCell(workflow.getModelId());
			table.addCell(String.format("%6d", workflow.getTasks().size()));
			table.addCell(String.format("%-7s", ""+workflow.getStatus()));
			++seqNo;
		}
		pw.println(table.render());
	}
	
	private void printInstancesLongList(List<Workflow> wfList, PrintWriter pw) {
		int seqNo = 1;
		for ( Workflow workflow : wfList ) {
			String tasksStr = FStream.from(workflow.getTasks())
										.map(task -> task.getTaskId())
										.join(", ");
			pw.append(CsvFormat.toLine(m_delimiter, seqNo, workflow.getName(), workflow.getModelId(),
										workflow.getStatus(), workflow.getTasks().size(), tasksStr));
			pw.println();
			
			++seqNo;
		}
	}
	
	private void printInstancesLongTable(List<Workflow> wfList, PrintWriter pw) {
		Table table = new Table(6);
		table.setColumnWidth(1, 15, 50);
		table.setColumnWidth(2, 10, 40);
		table.setColumnWidth(3, 9, 9);

		table.addCell(" # ");
		table.addCell(" ID ");
		table.addCell(" MODEL ");
		table.addCell(" STATUS ");
		table.addCell(" COUNT ");
		table.addCell(" TASKS ");
		
		int seqNo = 1;
		for ( Workflow workflow : wfList ) {
			table.addCell(String.format("%3d", seqNo));
			table.addCell(workflow.getName());
			table.addCell(workflow.getModelId());
			table.addCell(String.format("%7s", workflow.getStatus()));
			table.addCell(String.format("%6d", workflow.getTasks().size()));
			
			String tasksStr = FStream.from(workflow.getTasks())
									.map(task -> task.getTaskId())
									.join(", ");
			table.addCell(tasksStr);
			++seqNo;
		}
		pw.println(table.render());
	}
}
