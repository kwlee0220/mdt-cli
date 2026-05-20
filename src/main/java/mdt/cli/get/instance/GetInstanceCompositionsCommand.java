package mdt.cli.get.instance;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.barfuin.texttree.api.Node;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

import mdt.client.instance.HttpMDTInstanceClient;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTTwinCompositionDescriptor;
import mdt.model.instance.MDTTwinCompositionDescriptor.MDTCompositionDependency;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.TerminalNode;

import picocli.CommandLine.Command;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "compositions",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get Twin-Compositions for the MDTInstance."
)
public class GetInstanceCompositionsCommand extends AbstractInstanceSubCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetInstanceCompositionsCommand.class);

	public static final void main(String... args) throws Exception {
		main(new GetInstanceCompositionsCommand(), args);
	}
	
	public GetInstanceCompositionsCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw) throws SerializationException, IOException {
		MDTTwinCompositionDescriptor twinComp = instance.getMDTTwinCompositionDescriptor();
		String json = MDTModelSerDe.JSON_SERIALIZER.write(twinComp);
		pw.println(json);
	}

	@Override
	protected Node toMDTModelNode(MDTInstance service) throws IOException {
		return new TwinCompositionNode(service.getId(), service.getMDTTwinCompositionDescriptor());
	}
	
	private static class TwinCompositionNode extends DefaultNode {
		private List<TerminalNode> m_children;
		
		public TwinCompositionNode(String instId, MDTTwinCompositionDescriptor twinComp) {
			setTitle("Compositions");
			
			m_children = FStream.from(twinComp.getCompositionDependencies())
								.filter(dep -> dep.getSourceItem().equals(instId) && dep.getType().equals("contain"))
								.map(dep -> dep.getTargetItem())
								.map(comp -> sequencedItem(twinComp.getCompositionDependencies(), comp))
								.toList();
		}
		
		@Override
		public Iterable<? extends Node> getChildren() {
			return m_children;
		}
		
		private TerminalNode sequencedItem(List<MDTCompositionDependency> depList, String comp) {
			List<String> followers = FStream.from(depList)
											.filter(dep -> dep.getSourceItem().equals(comp) && dep.getType().equals("sequence"))
											.map(dep -> dep.getTargetItem())
											.toList();
			String followersText = switch (followers.size()) {
				case 0 -> "";
				case 1 -> String.format(" -> %s", followers.get(0));
				default -> String.format(" -> %s", followers);
			};
			String text = String.format("%s%s", comp, followersText);
			return TerminalNode.text(text);
		}
	}

}
