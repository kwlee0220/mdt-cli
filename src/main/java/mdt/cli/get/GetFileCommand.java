package mdt.cli.get;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.cli.AbstractMDTCommand;
import mdt.client.instance.HttpMDTInstanceManager;
import mdt.model.MDTManager;
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
		
		MDTElementReference iref = ElementReferences.parseExpr(m_elmRef);
		iref.activate(manager);
		
		File output = m_outputFile;
		if ( output == null ) {
			output = toDefaultOutputFile(iref.readAASFileValue());
		}
		iref.readAttachment(output);
		if ( m_verbose ) {
			out().printf("File downloaded to: %s%n", output.getAbsolutePath());
		}
	}

	/**
	 * '--file'이 생략된 경우 저장할 파일을 정한다.
	 * <p>
	 * File 요소의 값은 서버 쪽 경로이므로 그대로 쓰지 않고, 마지막 경로 구성요소(파일 이름)만 꺼내
	 * 현재 디렉토리에 저장한다. 그래야 절대 경로나 {@code ..}가 포함된 값으로 현재 디렉토리 밖에
	 * 파일을 쓰지 않는다.
	 */
	private File toDefaultOutputFile(FileValue fvalue) {
		String path = (fvalue != null) ? fvalue.getValue() : null;
		if ( path == null || path.isBlank() ) {
			throw new IllegalArgumentException("File element has no path value; specify the output file "
												+ "with '--file': ref=" + m_elmRef);
		}

		// 서버 경로의 구분자는 OS와 무관하므로 '/'와 '\'를 모두 구분자로 본다.
		String fileName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
		if ( fileName.isBlank() || fileName.equals(".") || fileName.equals("..") ) {
			throw new IllegalArgumentException("Cannot derive a file name from the File element value '"
												+ path + "'; specify the output file with '--file'");
		}

		return new File(fileName);
	}
}
