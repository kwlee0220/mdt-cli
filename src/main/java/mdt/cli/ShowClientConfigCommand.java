package mdt.cli;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.client.MDTClientConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@Command(
	name = "endpoints",
	parameterListHeading = "Parameters:%n",
	optionListHeading = "Options:%n",
	mixinStandardHelpOptions = true,
	description = "Get the endpoint for the client."
)
public class ShowClientConfigCommand extends AbstractCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(ShowClientConfigCommand.class);
	
	@Mixin private ClientConfigOptions m_clientConfig;

	public static final void main(String... args) throws Exception {
		main(new ShowClientConfigCommand(), args);
	}

	public ShowClientConfigCommand() {
		setLogger(s_logger);
	}

	/**
	 * MDTManager에 접속하지 않고, 찾은 클라이언트 설정을 출력한다.
	 *
	 * @throws IOException	설정 파일을 읽거나 해석하지 못한 경우.
	 */
	@Override
	protected void execute() throws IOException {
		m_clientConfig.getClientConfig()
					.ifPresent(this::printMDTClientConfig)
					.ifAbsent(() -> {
						err().println("Cannot get MDTManager's endpoint");
						markFailed();
					});
	}
	
	private void printMDTClientConfig(MDTClientConfig config) {
		out().println("- mdt-url: " + config.getMdtUrl());
		out().println("  . connect-timeout: " + config.getConnectTimeout());
		out().println("  . read-timeout: " + config.getReadTimeout());
		out().println("- workflow-endpoint: " + config.getWorkflowManagerUrl());
		out().println("- mqtt-endpoint: " + config.getMqttEndpoint());
	}
}
