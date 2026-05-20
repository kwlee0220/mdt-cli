package mdt.cli.list;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.nocrala.tools.texttablefmt.Table;

import utils.func.FOption;
import utils.func.Optionals;
import utils.func.Try;
import utils.stream.FStream;

import mdt.cli.CommandCollection;

import picocli.CommandLine.Command;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */

@Command(
	name="list",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description="List all MDT entities (MDT instances, shells, submodels, operations, workflow models, workflows).",
	subcommands= {
		ListMDTInstanceCommand.class,
		ListShellCommand.class,
		ListSubmodelCommand.class,
		ListOperationCommand.class,
		ListWorkflowModelAllCommand.class,
		ListWorkflowAllCommand.class,
	})
public class ListCommands extends CommandCollection {
	public static final String DELIM = "|";
	
	public interface ListCollector {
		public void collectLine(Object[] cols);
		public String getFinalString();
	}

	static class CSVCollector implements ListCollector {
		private final String m_delim;
		private ByteArrayOutputStream m_baos;
		private PrintWriter m_writer;
		
		CSVCollector(String delim) {
			m_delim = delim;
			m_baos = new ByteArrayOutputStream();
			m_writer = new PrintWriter(m_baos);
		}
	
		@Override
		public void collectLine(Object[] cols) {
			m_writer.println(FStream.of(cols)
									.map(c -> Optionals.getOrElse(c,""))
									.join(m_delim));
		}
		
		void addLine(String line) {
		}
		
		@Override
		public String getFinalString() {
			m_writer.close();
			Try.run(m_baos::close);
			return m_baos.toString();
		}
	}

	static class TableCollector implements ListCollector {
		private Table m_table;
		
		TableCollector(Table table) {
			m_table = table;
		}
	
		@Override
		public void collectLine(Object[] cols) {
			FStream.of(cols)
				    .map(col -> FOption.mapOrElse(col, c-> ""+c, ""))
					.forEach(m_table::addCell);
		}
		
		@Override
		public String getFinalString() {
			return m_table.render();
		}
	}
}
