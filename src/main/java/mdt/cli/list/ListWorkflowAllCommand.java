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
import mdt.workflow.Workflow;
import mdt.workflow.WorkflowManager;

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
public class ListWorkflowAllCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ListWorkflowAllCommand.class);
	
	@Option(names={"--table", "-t"}, description="display instances in a table format.")
	private boolean m_tableFormat = false;
	
	@Option(names={"--glob", "-g"}, paramLabel="expr", required=false,
			description="glob pattern to filter workflows.")
	private String m_glob = null;

	@Option(names={"--long", "-l"}, description="show detailed information.")
	private boolean m_long = false;

	@Option(names={"--delimiter", "-d"}, paramLabel="delimiter",
					description="delimiter (for 'csv' output only)")
	private String m_delimiter = ListCommands.DELIM;

	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\"")
	private String m_repeat = null;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new ListWorkflowAllCommand(), args);
	}
	
	public ListWorkflowAllCommand() {
		setLogger(s_logger);
	}

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
					catch ( Exception ignored ) {
						pw.println("failed to list Workflow instances: cause=" + ignored);
					}
				}
			};
			pwriter.setVerbose(m_verbose);
			pwriter.run();
		}
	}
	
	private void printOutput(WorkflowManager wfManager, PrintWriter pw) throws InterruptedException {
        try {
			List<Workflow> wfList = listWorkflows(wfManager);
			if ( m_long ) {
				if ( m_tableFormat ) {
					printInstancesLongTable(wfList, pw);
				}
				else {
			    	printInstancesLongList(wfList, pw);
				}
			}
			else {
				if ( m_tableFormat ) {
					printInstancesShortTable(wfList, pw);
				}
				else {
					printInstancesShortList(wfList, pw);
				}
			}
		}
		catch ( ResourceNotFoundException e ) {
			pw.println("fails to list MDTWorkflows: " + e.getCause());
		}
		catch ( RESTfulIOException e ) {
			Throwable cause = e.getCause();
			if ( cause instanceof InterruptedIOException ) {
				throw new InterruptedException("interrupted while listing MDTWorkflows");
			}
			else {
				pw.println("fails to list MDTWorkflows: " + e.getCause());
			}
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
			pw.append(""+seqNo).append(m_delimiter);
			pw.append(workflow.getName()).append(m_delimiter);
			pw.append(workflow.getModelId()).append(m_delimiter);
			pw.append(""+workflow.getTasks().size()).append(m_delimiter);
			pw.append(String.format("%s", workflow.getStatus()));
			pw.println();
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
			pw.append(""+seqNo).append(m_delimiter);
			pw.append(workflow.getName()).append(m_delimiter);
			pw.append(workflow.getModelId()).append(m_delimiter);
			pw.append(String.format("%s", workflow.getStatus())).append(m_delimiter);
			pw.append(String.format("%d", workflow.getTasks().size())).append(m_delimiter);
			String tasksStr = FStream.from(workflow.getTasks())
										.map(task -> task.getTaskId())
										.join(", ");
			pw.append(tasksStr);
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
