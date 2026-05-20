package mdt.cli;

import utils.UsageHelp;

import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandCollection implements Runnable {
	@Spec private CommandSpec m_spec;
	@Mixin private UsageHelp m_help;
	
	@Override
	public void run() {
		m_spec.commandLine().usage(System.out, Ansi.OFF);
	}
}