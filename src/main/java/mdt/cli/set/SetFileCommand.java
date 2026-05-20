package mdt.cli.set;

import java.io.File;

import org.apache.tika.Tika;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;

import utils.func.Optionals;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.SubmodelService;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.FileValue;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "file",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "set File SubmodelElement."
)
public class SetFileCommand extends AbstractMDTCommand {
	@Parameters(index="0", arity="1", paramLabel="element-ref", description="target SubmodelElementReference to set")
	private String m_target = null;
	
	@Parameters(index="1", arity="1", paramLabel="path", description="file path to set")
	private File m_file;
	
	@Option(names = {"--path"}, paramLabel="value for File", required=false)
	private String m_path;
	
	@Option(names = {"--mimeType"}, paramLabel="contentType for File", required=false)
	private String m_mimeType;

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		ElementReference ref = ElementReferences.parseExpr(m_target);
		if ( !(ref instanceof MDTElementReference) ) {
			throw new IllegalArgumentException("Target element is not MDTElementReference: " + ref);
		}
		MDTElementReference targetRef = (MDTElementReference)ref;
		targetRef.activate(manager);
		
		SubmodelElement sme = targetRef.read();
		if ( !(sme instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) ) {
			throw new IllegalArgumentException("Target element is not a File: " + targetRef);
		}
		
		SubmodelService svc = targetRef.getSubmodelService();
		
		String path = Optionals.getOrElse(m_path, m_file::getName);
		String contentType = m_mimeType;
		if ( contentType == null ) {
			contentType = new Tika().detect(m_file);
		}
		
		FileValue fvalue = new FileValue(path, contentType);
		svc.putAttachmentByPath(targetRef.getIdShortPathString(), fvalue, m_file);
	}

	public static void main(String... args) throws Exception {
		main(new SetFileCommand(), args);
	}
}