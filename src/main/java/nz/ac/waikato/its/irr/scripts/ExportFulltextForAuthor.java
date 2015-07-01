package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.dspace.app.bulkedit.DSpaceCSV;
import org.dspace.app.bulkedit.MetadataExport;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the UoW Institutional Research Repositories
 */
public class ExportFulltextForAuthor {
	private static final Options OPTIONS = new Options();

	static {
		Option option = new Option("a", "author", true, "The name of the author for which to do the export, in \"lastname, firstname\" format including the double quotes. Required.");
		option.setRequired(true);
		OPTIONS.addOption(option);
		option = new Option("d", "dir", true, "Destination directory for the export. The directory will be created if it doesn't exist and must be writeable by the user running the script. Required.");
		option.setRequired(true);
		OPTIONS.addOption(option);
		OPTIONS.addOption("m", "metadata", false, "If given, also export metadata CSV for all items by this author.");
		OPTIONS.addOption("h", "help", false, "Print help for this command and exit without taking any action.");
	}

	public static void main(String[] args) {
		CommandLine line = null;
		try {
			line = new BasicParser().parse(OPTIONS, args);
		} catch (ParseException e) {
			System.err.println("Could not parse command line options: " + e.getMessage());
			ScriptUtils.printHelpAndExit(ExportFulltextForAuthor.class.getSimpleName(), 1, OPTIONS);
		}

		if (line == null || line.hasOption("h")) {
			ScriptUtils.printHelpAndExit(ExportFulltextForAuthor.class.getSimpleName(), 0, OPTIONS);
		}

		File destDir = new File(line.getOptionValue("d"));
		if (!destDir.exists()) {
			try {
				Files.createDirectories(destDir.toPath());
				System.out.println("Created destination directory " + destDir.getCanonicalPath());
			} catch (IOException e) {
				System.err.println("Destination directory " + line.getOptionValue("d") + " doesn't exist but could not be created: " + e.getMessage());
				e.printStackTrace(System.err);
				ScriptUtils.printHelpAndExit(ExportFulltextForAuthor.class.getSimpleName(), 1, OPTIONS);
			}
		}

		if (!destDir.exists() || !destDir.canWrite()) {
			System.err.println("Destination directory still doesn't exist or isn't writable by current user.");
			ScriptUtils.printHelpAndExit(ExportFulltextForAuthor.class.getSimpleName(), 1, OPTIONS);
		}

		String authorName = line.getOptionValue("a");
		Context context = null;
		try {
			context = new Context(Context.READ_ONLY);
			context.turnOffAuthorisationSystem();
			ItemIterator items = getAuthorItems(context, authorName);
			while (items.hasNext()) {
				Item item = items.next();
				System.out.println("Processing item id=" + item.getID());
				Bundle[] contentBundles = item.getBundles(Constants.CONTENT_BUNDLE_NAME);
				for (Bundle bundle : contentBundles) {
					Bitstream[] bitstreams = bundle.getBitstreams();
					for (Bitstream bitstream : bitstreams) {
						System.out.println("Processing bitstream id=" + bitstream.getID() + ", name=" + bitstream.getName());
						Path target = new File(destDir, item.getID() + "_" + bitstream.getID() + "_" + bitstream.getName()).toPath();
						try {
							Files.copy(bitstream.retrieve(), target);
							System.out.println("Created file " + target.toString());
						} catch (IOException e) {
							System.err.println("Could not save this bitstream, skipping. Reason: " + e.getMessage());
						}
					}
				}
				item.decache();
			}
			if (line.hasOption("m")) {
				System.out.println("Exporting metadata");
				items = getAuthorItems(context, authorName);
				MetadataExport mdExport = new MetadataExport(context, items, true);
				DSpaceCSV csv = mdExport.export();
				String filename = destDir.getCanonicalPath() + File.separator + "metadata.csv";
				csv.save(filename);
				System.out.println("Exported metadata to file " + filename);
			}
		} catch (SQLException | AuthorizeException | IOException e) {
			e.printStackTrace(System.err);
		} finally {
			if (context != null && context.isValid()) {
				context.abort();
			}
		}
	}

	private static ItemIterator getAuthorItems(Context context, String authorName) throws SQLException, AuthorizeException, IOException {
		return Item.findByMetadataField(context, "dc", "contributor", "author", authorName);
	}

}
