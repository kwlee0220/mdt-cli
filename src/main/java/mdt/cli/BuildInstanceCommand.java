package mdt.cli;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.model.MDTManager;
import mdt.task.builtin.MultiVariablesCommand;

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
public class BuildInstanceCommand extends MultiVariablesCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(BuildInstanceCommand.class);

	@Parameters(index="0", paramLabel="id", description="MDTInstance identifier")
	private String m_instanceId;

	@Parameters(index="1", paramLabel="dir", description="Path to the MDTInstance directory")
	private Path m_instanceDir;
	
	public BuildInstanceCommand() {
		setLogger(s_logger);
	}

	@Override
	public void run(MDTManager mdt) throws Exception {
        Path zipFile = Paths.get(String.format("%s.zip", m_instanceId));

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            Files.walk(m_instanceDir)
                 .filter(path -> !Files.isDirectory(path))
                 .forEach(path -> {
                     ZipEntry zipEntry = new ZipEntry(m_instanceDir.relativize(path).toString());
                     try {
                         zos.putNextEntry(zipEntry);
                         Files.copy(path, zos);
                         zos.closeEntry();
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 });
        }

        System.out.println("MDTInstance zip file created: " + zipFile.toAbsolutePath());
	}

	public static void main(String... args) throws Exception {
		main(new BuildInstanceCommand(), args);
		System.exit(0);
	}
}
