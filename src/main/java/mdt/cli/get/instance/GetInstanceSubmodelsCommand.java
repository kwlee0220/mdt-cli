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
import mdt.model.AASUtils;
import mdt.model.MDTModelSerDe;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTSubmodelDescriptor;
import mdt.tree.node.DefaultNode;
import mdt.tree.node.TerminalNode;

import picocli.CommandLine.Command;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "submodels",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get submodel informations for the MDTInstance."
)
public class GetInstanceSubmodelsCommand extends AbstractInstanceSubCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetInstanceSubmodelsCommand.class);

	public static final void main(String... args) throws Exception {
		main(new GetInstanceSubmodelsCommand(), args);
	}
	
	public GetInstanceSubmodelsCommand() {
		setLogger(s_logger);
	}

	@Override
	protected void displayAsJson(HttpMDTInstanceClient instance, PrintWriter pw) throws SerializationException, IOException {
		List<MDTSubmodelDescriptor> descList = instance.getMDTSubmodelDescriptorAll();
		String json = MDTModelSerDe.JSON_SERIALIZER.writeList(descList);
		pw.println(json);
	}

	@Override
	protected Node toMDTModelNode(MDTInstance instance) throws IOException {
		return new SubmodelDescriptorNode(instance.getMDTSubmodelDescriptorAll());
	}
	
	private static class SubmodelDescriptorNode extends DefaultNode {
		private List<? extends DefaultNode> m_children;

		public SubmodelDescriptorNode(List<MDTSubmodelDescriptor> submodels) {
			setTitle("Submodels");
			
			m_children = FStream.from(submodels)
								.map(SubmodelInfoNode::new)
								.toList();
		}

		@Override
		public Iterable<? extends Node> getChildren() {
			return m_children;
		}
		
		private static class SubmodelInfoNode extends DefaultNode {
			private List<? extends DefaultNode> m_children;

			public SubmodelInfoNode(MDTSubmodelDescriptor sm) {
				setTitle(sm.getIdShort());
				
				String smIdEncoded = AASUtils.encodeBase64UrlSafe(sm.getId());
				m_children = List.of(new TerminalNode("id", "", sm.getId()),
									new TerminalNode("idEncoded", "", smIdEncoded),
									new TerminalNode("semanticId", "", sm.getSemanticId()),
									new TerminalNode("endpoint", "", sm.getEndpoint()));
			}

			@Override
			public Iterable<? extends Node> getChildren() {
				return m_children;
			}
		}
	}
}
