package mdt.cli.get;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.CancellationException;

import org.barfuin.texttree.api.Node;
import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.UnitUtils;

import mdt.cli.AbstractMDTCommand;
import mdt.cli.PeriodicRefreshingConsole;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.ElementValue;
import mdt.tree.node.DefaultNodeFactories;

import picocli.CommandLine.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractGetElementCommand extends AbstractMDTCommand {
	protected static final TreeOptions TREE_OPTS = new TreeOptions();
	static {
		TREE_OPTS.setStyle(TreeStyles.UNICODE_ROUNDED);
		TREE_OPTS.setMaxDepth(5);
	}
	
	@Option(names={"--output", "-o"}, paramLabel="type", required=false,
			description="output type (candidnates: tree, json, or value)")
	protected String m_output = "tree";

	@Option(names={"--repeat", "-r"}, paramLabel="interval",
			description="repeat interval (e.g. \"1s\", \"500ms\"")
	protected String m_repeat = null;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	protected void run(MDTInstanceManager manager, ElementReference elmRef) throws Exception {
		if ( m_repeat == null ) {
			try ( PrintWriter pw = new PrintWriter(System.out, true) ) {
				printOutput(elmRef, pw);
			}
			return;
		}
		else {
			Duration repeatInterval = UnitUtils.parseDuration(m_repeat);
			PeriodicRefreshingConsole pwriter = new PeriodicRefreshingConsole(repeatInterval) {
				@Override
				protected void print(PrintWriter pw) {
					try {
						if ( elmRef instanceof MDTElementReference iref && !iref.isActivated()) {
							iref.activate(manager);
						}
						printOutput(elmRef, pw);
					}
					catch ( Exception ignored ) {
						pw.println("" + ignored);
					}
				}
			};
			pwriter.setVerbose(m_verbose);
			try {
				pwriter.run();
			}
			catch ( CancellationException | InterruptedException e ) { }
		}
	}
	
	protected void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception {
		String outputString = switch ( m_output ) {
			case "tree" -> toDisplayTree(smeRef.read(), TREE_OPTS);
			case "json" -> toDisplayJson(smeRef.read());
			case "value" -> toDisplayValue(smeRef.readValue());
			default -> throw new IllegalArgumentException("Invalid output type: " + m_output);
		};
		pw.print(outputString);
		pw.println();
	}

	protected String toDisplayTree(SubmodelElement target, TreeOptions opts) throws Exception {
		Node topNode = DefaultNodeFactories.create(target);
		return TextTree.newInstance(opts).render(topNode);
	}

	protected String toDisplayJson(SubmodelElement target) throws Exception {
		return MDTModelSerDe.toJsonString(target);
	}

	protected String toDisplayValue(ElementValue value) throws Exception {
		return value.toDisplayString();
	}
}
