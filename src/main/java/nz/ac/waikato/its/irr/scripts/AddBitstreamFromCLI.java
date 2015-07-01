package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import java.io.*;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the UoW Institutional Research Repositories
 */
public class AddBitstreamFromCLI {
	private static final Options OPTIONS = new Options();

	static {
		Option option = new Option("i", "identifier", true, "Handle or ID of item to add bitstream to");
		option.setRequired(true);
		OPTIONS.addOption(option);
		option = new Option("f", "file", true, "File to add");
		option.setRequired(true);
		OPTIONS.addOption(option);
		OPTIONS.addOption("d", "description", true, "The file description (optional)");
		OPTIONS.addOption("b", "bundle", true, "Name of the bundle that this file should be added to (optional). If not given, " + Constants.DEFAULT_BUNDLE_NAME + " is used.");
		OPTIONS.addOption("h", "help", false, "Print help for this command and exit without taking any action.");
	}

	public static void main(String[] args) {
		CommandLine line = null;
		try {
			line = new BasicParser().parse(OPTIONS, args);
		} catch (ParseException e) {
			System.err.println("Could not parse command line options: " + e.getMessage());
			ScriptUtils.printHelpAndExit(AddBitstreamFromCLI.class.getSimpleName(), 1, OPTIONS);
		}

		if (line == null || line.hasOption("h")) {
			ScriptUtils.printHelpAndExit(AddBitstreamFromCLI.class.getSimpleName(), 0, OPTIONS);
		}

		File file = new File(line.getOptionValue("f"));
		if (!file.exists() || !file.canRead()) {
			System.err.println("File " + line.getOptionValue("f") + " doesn't exist or isn't readable. Exiting.");
			ScriptUtils.printHelpAndExit(AddBitstreamFromCLI.class.getSimpleName(), 1, OPTIONS);
		}

		Context context = null;
		try {
			context = new Context();
			context.turnOffAuthorisationSystem();

			Item item = null;
			String identifier = line.getOptionValue("i");
			if (identifier.matches("^\\d+$")) {
				try {
					item = Item.find(context, Integer.valueOf(identifier));
				} catch (NumberFormatException e) {
					e.printStackTrace(System.err);
				}
			} else {
				DSpaceObject dso = HandleManager.resolveToObject(context, identifier);
				if (dso != null && dso.getType() == Constants.ITEM) {
					item = (Item) dso;
				}
			}
			if (item == null) {
				System.err.println("Could not find item with identifier " + identifier + ", exiting.");
				context.abort();
				return;
			}

			System.out.println("Item id=" + item.getID());

			String bundleName = Constants.CONTENT_BUNDLE_NAME;
			if (line.hasOption("b")) {
				bundleName = line.getOptionValue("b");
			}

			Bundle bundle = null;
			Bundle[] bundles = item.getBundles(bundleName);
			if (bundles != null && bundles.length > 0 && bundles[0] != null) {
				bundle = bundles[0];
			}
			if (bundle == null) {
				System.out.println("No bundle with name " + bundleName + " found, creating one.");
				bundle = item.createBundle(bundleName);
			}
			Bitstream bitstream = bundle.createBitstream(new BufferedInputStream(new FileInputStream(file)));
			System.out.println("Uploaded file " + file.getName() + " to bundle " + bundleName);
			bitstream.setName(file.getName());
			if (line.hasOption("d")) {
				bitstream.setDescription(line.getOptionValue("d"));
			}
			try {
				TikaConfig tika = new TikaConfig();
				Metadata metadata = new Metadata();
				metadata.set(Metadata.RESOURCE_NAME_KEY, file.toString());
				String mimetype = tika.getDetector().detect(TikaInputStream.get(file), metadata).toString();
				if (StringUtils.isNotBlank(mimetype)) {
					System.out.println("Trying to set format to " + mimetype);
					bitstream.setFormat(BitstreamFormat.findByMIMEType(context, mimetype));
				}
			} catch (TikaException e) {
				System.err.println("Problem detecting format of file, not setting format.");
				e.printStackTrace(System.err);
			}
			bitstream.update();
			item.update();

			context.complete();

			System.out.println("File successfully added to item. You may wish to delete the original from " + file.getCanonicalPath());
		} catch (SQLException | AuthorizeException | IOException e) {
			System.err.println("Exception encountered while processing command");
			e.printStackTrace(System.err);
		} finally {
			if (context != null && context.isValid()) {
				context.abort();
			}
		}

	}
}
