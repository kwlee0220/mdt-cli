package mdt.cli.get.instance;

import java.io.IOException;
import java.io.PrintWriter;

import org.barfuin.texttree.api.Node;
import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.StopWatch;
import utils.UnitUtils;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceClient;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstance;

import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractInstanceSubCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractInstanceSubCommand.class);
	
	private static final TreeOptions TREE_OPTS = new TreeOptions();
	static {
		TREE_OPTS.setStyle(TreeStyles.UNICODE_ROUNDED);
		TREE_OPTS.setMaxDepth(5);
	}

	@ParentCommand GetInstanceCommand m_parent;
	
	@Option(names={"--output", "-o"}, paramLabel="type", defaultValue="tree", required=false,
			description="output type (candidnates: 'tree' or 'json')")
	private String m_output = "tree";
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;
	
	abstract protected Node toMDTModelNode(MDTInstance instance) throws IOException;
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw) throws SerializationException, IOException { }
	
	protected AbstractInstanceSubCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		StopWatch watch = StopWatch.start();
		
		HttpMDTInstanceClient inst = m_parent.getInstance(mdt);
		try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
			switch ( m_output ) {
				case "tree":
					displayAsTree(inst, pw);
					break;
				case "json":
					displayAsJson(inst, pw);
					break;
				default:
					throw new IllegalArgumentException("unsupported output type: " + m_output);
			}
			
			if ( m_verbose ) {
				double elapsed = watch.getElapsedInFloatingSeconds();
				String secStr = UnitUtils.toMillisString(Math.round(elapsed * 1000));
				pw.println("elapsed: " + secStr);
			}
		}
	}
	
	private void displayAsTree(HttpMDTInstanceClient service, PrintWriter pw) throws IOException {
		Node mdtModelNode = toMDTModelNode(service);
		String treeString = TextTree.newInstance(TREE_OPTS).render(mdtModelNode);
		pw.print(treeString);
	}
}
