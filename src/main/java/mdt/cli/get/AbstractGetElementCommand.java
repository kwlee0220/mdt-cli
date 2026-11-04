package mdt.cli.get;

import java.io.PrintWriter;

import org.barfuin.texttree.api.Node;
import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.jetbrains.annotations.Nullable;


import mdt.cli.AbstractMDTCommand;
import mdt.cli.RepeatOptions;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.ElementValue;
import mdt.tree.node.DefaultNodeFactories;

import picocli.CommandLine.Mixin;
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
	
	/** 요소 하나를 출력하는 명령의 {@code --output} 값. */
	enum ElementOutputType { tree, json, value };

	@Mixin private RepeatOptions m_repeatOpts;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	protected void run(MDTInstanceManager manager, ElementReference elmRef) throws Exception {
		m_repeatOpts.run(pw -> {
			if ( elmRef instanceof MDTElementReference iref && !iref.isActivated() ) {
				iref.activate(manager);
			}
			printOutput(elmRef, pw);
		}, "read " + elmRef, m_verbose);
	}
	
	/**
	 * 참조가 가리키는 요소를 한 번 출력한다. 출력 형식({@code --output})은 하위 명령이 정한다.
	 *
	 * @param smeRef	출력할 요소의 참조(활성화되어 있다).
	 * @param pw		출력 대상.
	 * @throws Exception	요소를 읽지 못한 경우.
	 */
	protected abstract void printOutput(ElementReference smeRef, PrintWriter pw) throws Exception;
	
	/**
	 * 요소를 주어진 형식(트리, 전체 JSON, 값)으로 출력한다.
	 *
	 * @param smeRef	출력할 요소의 참조.
	 * @param pw		출력 대상.
	 * @param output	출력 형식.
	 * @throws Exception	요소를 읽지 못한 경우.
	 */
	protected void printElement(ElementReference smeRef, PrintWriter pw, ElementOutputType output) throws Exception {
		String outputString = switch ( output ) {
			case tree -> toDisplayTree(smeRef.read(), TREE_OPTS);
			case json -> toDisplayJson(smeRef.read());
			case value -> toDisplayValue(smeRef.readValue());
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

	protected String toDisplayValue(@Nullable ElementValue value) throws Exception {
		return (value != null) ? value.toDisplayString() : "(none)";
	}
}
