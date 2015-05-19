package nz.ac.lconz.irr.scripts;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class ScriptUtils {
	static void printHelpAndExit(String name, int exitCode, Options options) {
		new HelpFormatter().printHelp(name + " options", options);
		System.exit(exitCode);
	}
}
