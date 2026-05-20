package mdt.test;

import org.barfuin.texttree.api.TextTree;
import org.barfuin.texttree.api.TreeOptions;
import org.barfuin.texttree.api.style.TreeStyles;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;

import mdt.client.resource.HttpSubmodelServiceClient;
import mdt.model.AASUtils;
import mdt.model.sm.info.DefaultInformationModel;
import mdt.tree.MDTInstanceNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TestInformationModel {
	public static final void main(String... args) throws Exception {
//		JsonSerializer ser = new JsonSerializer();

		int port = 19001;
		String assetId = "Welder";
		
		String submodelId = String.format("https://www.samcheon.com/mdt/%s/sm/InformationModel", assetId);
		String url = String.format("https://localhost:%d/api/v3.0/submodels/%s", port,
									AASUtils.encodeBase64UrlSafe(submodelId));
		HttpSubmodelServiceClient svc = HttpSubmodelServiceClient.newTrustAllSubmodelServiceClient(url);
		
		Submodel submodel = svc.getSubmodel();
		DefaultInformationModel infoModel = new DefaultInformationModel();
		infoModel.updateFromAasModel(submodel);
		
		System.out.println("id=" + infoModel.getId());
		System.out.println("idShort=" + infoModel.getIdShort());
		System.out.println("semanticId=" + infoModel.getSemanticId());

		System.out.println(infoModel.getMDTInfo());
		System.out.println(infoModel.getTwinComposition());
		
		TreeOptions opts = new TreeOptions();
		opts.setStyle(TreeStyles.UNICODE_ROUNDED);
		opts.setMaxDepth(5);
		
		MDTInstanceNode root = MDTInstanceNode.builder()
										.mdtId("KRCW-02ER1A101")
										.informationModelSubmodel(infoModel)
										.build();
		System.out.print("\033[2J\033[1;1H");
		System.out.print(TextTree.newInstance(opts).render(root));
	}
}
