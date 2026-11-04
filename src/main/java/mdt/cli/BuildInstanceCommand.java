package mdt.cli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "build",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Build MDTInstance zip file."
)
public class BuildInstanceCommand extends AbstractCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(BuildInstanceCommand.class);

	@Parameters(index="0", paramLabel="instance-id", description="MDTInstance identifier")
	private String m_instanceId;

	@Parameters(index="1", paramLabel="dir", description="Path to the MDTInstance directory")
	private Path m_instanceDir;
	
	public BuildInstanceCommand() {
		setLogger(s_logger);
	}

	/**
	 * MDTManager에 접속하지 않고 zip 파일을 만든다(로컬 파일만 다룬다).
	 *
	 * @throws IOException	파일을 읽거나 zip 파일을 쓰지 못한 경우.
	 */
	@Override
	protected void execute() throws IOException {
		buildZipFile();
	}
	
	private void buildZipFile() throws IOException {
		if ( !Files.isDirectory(m_instanceDir) ) {
			throw new IllegalArgumentException("Not a directory: " + m_instanceDir);
		}
		
		Path zipFile = Paths.get(String.format("%s.zip", m_instanceId)).toAbsolutePath().normalize();
		try ( ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)));
				Stream<Path> paths = Files.walk(m_instanceDir) ) {
			for ( Path path: (Iterable<Path>)paths::iterator ) {
				// 대상 디렉토리 안에서 실행하면 만들고 있는 zip 파일 자신도 탐색되므로 제외한다.
				if ( !Files.isRegularFile(path) || path.toAbsolutePath().normalize().equals(zipFile) ) {
					continue;
				}
				
				// zip 항목 이름은 OS와 상관없이 '/'로 구분한다.
				String entryName = m_instanceDir.relativize(path).toString().replace(File.separatorChar, '/');
				zos.putNextEntry(new ZipEntry(entryName));
				Files.copy(path, zos);
				zos.closeEntry();
			}
		}

		out().println("MDTInstance zip file created: " + zipFile);
	}

	public static void main(String... args) throws Exception {
		main(new BuildInstanceCommand(), args);
	}
}
