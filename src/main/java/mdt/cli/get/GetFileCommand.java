package mdt.cli.get;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;
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
	description = "Get a file from the File SubmodelElement."
)
public class GetFileCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(GetFileCommand.class);

	@Parameters(index="0", arity="1", paramLabel="element-ref",
				description="Target File SubmodelElement reference")
	private String m_elmRef = null;
	
	@Option(names={"--file", "-f"}, paramLabel="path", description="file path to save output")
	private File m_outputFile;
	
	@Option(names={"-v"}, description="verbose")
	private boolean m_verbose = false;

	public static final void main(String... args) throws Exception {
		main(new GetFileCommand(), args);
	}
	
	public GetFileCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
		HttpMDTInstanceManager manager = (HttpMDTInstanceManager)mdt.getInstanceManager();
		
		ElementReference smeRef = ElementReferences.parseExpr(m_elmRef);
		if ( !(smeRef instanceof MDTElementReference) ) {
			throw new IllegalArgumentException("Target element reference must be an instance of MDTElementReference:"
												+ smeRef);
		}
		
		MDTElementReference iref = (MDTElementReference)smeRef;
		iref.activate(manager);
		
		FileValue fvalue = iref.readAASFileValue();
		File output = m_outputFile;
		if ( m_outputFile == null ) {
			output = new File(fvalue.getValue());
		}
		iref.readAttachment(output);
		if ( m_verbose ) {
			System.out.printf("File downloaded to: %s%n", output.getAbsolutePath());
		}
	}
}
