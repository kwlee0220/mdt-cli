package mdt.cli.set;

import java.io.File;

import mdt.cli.AbstractMDTCommand;
import mdt.model.MDTManager;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReferences;
import mdt.model.sm.ref.MDTElementReference;

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
	
	@Option(names = {"--mime-type"}, paramLabel="contentType for File", required=false)
	private String m_mimeType;
	
	/** 이전 옵션 이름 {@code --mimeType}(도움말에는 표시하지 않는다). */
	@Option(names = {"--mimeType"}, hidden=true)
	private void setMimeTypeAlias(String mimeType) {
		m_mimeType = mimeType;
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
		MDTInstanceManager manager = mdt.getInstanceManager();
		
		MDTElementReference targetRef = ElementReferences.parseExpr(m_target);
		targetRef.activate(manager);
		
		AbstractSetCommand.putAttachment(targetRef, m_file, m_path, m_mimeType);
	}

	public static void main(String... args) throws Exception {
		main(new SetFileCommand(), args);
	}
}