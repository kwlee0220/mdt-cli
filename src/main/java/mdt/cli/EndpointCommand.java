package mdt.cli;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.client.MDTClientConfig;
import mdt.model.MDTManager;

import picocli.CommandLine.Command;

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
public class EndpointCommand extends AbstractMDTCommand {
	private static final Logger s_logger = LoggerFactory.getLogger(EndpointCommand.class);
	private static final String CLIENT_CONFIG_FILE = "mdt_client_config.yaml";

	public static final void main(String... args) throws Exception {
		main(new EndpointCommand(), args);
	}

	public EndpointCommand() {
		setLogger(s_logger);
	}
	
	@Override
	public final void run() {
		try {
			getClientConfig()
				.ifPresent(config -> {
					printMDTClientConfig(config);
				})
				.ifAbsent(() -> {
					System.err.println("Cannot get MDTManager's endpoint");
				});
		}
		catch ( IOException e ) {
			System.err.printf("Failed to read client configuration from %s: %s%n", CLIENT_CONFIG_FILE, e.getMessage());
		}
	}

	@Override
	protected void run(MDTManager mdt) throws Exception {
		throw new AssertionError();
	}
	
	private void printMDTClientConfig(MDTClientConfig config) {
		System.out.println("- mdt-url: " + config.getMdtUrl());
		System.out.println("  . connect-timeout: " + config.getConnectTimeout());
		System.out.println("  . read-timeout: " + config.getReadTimeout());
		System.out.println("- workflow-endpoint: " + config.getWorkflowManagerUrl());
		System.out.println("- mqtt-endpoint: " + config.getMqttEndpoint());
	}
}
