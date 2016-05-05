/*
 * This file is a part of the lconz-scripts project.
 * The contents of this file are subject to the license and copyright detailed in the LICENSE file at the root of the source tree.
 */

package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.workflow.WorkflowItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *         for the University of Waikato's Institutional Research Repositories
 */
public class RetrospectiveElementsLinkup {
    private static final Options OPTIONS = new Options();

    static {
        Option option = new Option("f", "file", true, "File with comma-separated pairs of DSpace id, Elements pubs id (one per line). Mutually exclusive with -d, -p");
        OPTIONS.addOption(option);
        option = new Option("d", "dspace", true, "DSpace id or handle of single item to link up. If given, -p is also required. Mutually exclusive with -f.");
        OPTIONS.addOption(option);
        option = new Option("p", "pubs", true, "Elements pubs id of single item to link up. If given, -d is also required. Mutually exclusive with -f");
        OPTIONS.addOption(option);
        OPTIONS.addOption("h", "help", false, "Print help for this command and exit without taking any action.");
        OPTIONS.addOption("v", "verbose", false, "Report every pair that was linked up. If not given, only errors will be reported.");
    }

    public static void main(String[] args) {
        CommandLine line = null;
        try {
            line = new BasicParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            System.err.println("Could not parse command line options: " + e.getMessage());
            ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 1, OPTIONS);
        }

        if (line == null || line.hasOption("h")) {
            ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 0, OPTIONS);
        }

        if (line.hasOption("f") && (line.hasOption("d") || line.hasOption("p"))) {
            System.err.println("Cannot give both -f and -d/-p; use -f for bulk operation, -d/-p to link a single item.");
            ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 1, OPTIONS);
        }

        if (!line.hasOption("f") && !line.hasOption("d") && !line.hasOption("p")) {
            System.err.println("Need either -f (for bulk operation) or -d and -p (to link a single item).");
            ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 1, OPTIONS);
        }

        boolean verbose = line.hasOption("v");

        int itemsProcessed = 0;
        Context context = null;
        try {
            context = new Context();
            context.turnOffAuthorisationSystem();

            if (line.hasOption("f")) {
                String fileName = line.getOptionValue("f");
                File inputFile = new File(fileName);
                if (!inputFile.exists() || !inputFile.canRead()) {
                    System.err.println("Input file " + fileName + " doesn't exist or is not readable for current user.");
                    ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 1, OPTIONS);
                }
                try (Scanner scanner = new Scanner(inputFile)) {
                    while (scanner.hasNextLine()) {
                        String nextLine = scanner.nextLine();
                        if (nextLine.startsWith("#")) {
                            continue;
                        }
                        String[] toProcess = nextLine.trim().split(",\\s*");
                        if (toProcess.length != 2) {
                            System.err.println("Skipping line, was expecting comma-separated pair of DSpace id, Elements pubs id");
                            continue;
                        }
                        try {
                            processLinkup(context, toProcess[0], toProcess[1], verbose);
                            if (verbose) {
                                System.out.println(String.format("Linked up DSpace id/handle %s with Elements pubs id %s",
                                        toProcess[0],
                                        toProcess[1]));
                            }
                            context.commit();
                            itemsProcessed++;
                        } catch (Exception e) {
                            System.err.println(String.format(
                                    "Caught exception while attempting to link up DSpace id %s and publications id %s, skipping line",
                                    toProcess[0],
                                    toProcess[1]));
                            e.printStackTrace(System.err);
                        }
                    }
                } catch (FileNotFoundException e) {
                    System.err.println("Error processing file " + fileName);
                }
            } else if (!line.hasOption("d") || !line.hasOption("p")) {
                System.err.println("In single item mode, -d and -p are both required.");
                ScriptUtils.printHelpAndExit(RetrospectiveElementsLinkup.class.getSimpleName(), 1, OPTIONS);
            } else {
                String dspaceString = line.getOptionValue("d", "");
                String pubsString = line.getOptionValue("p", "");

                processLinkup(context, dspaceString, pubsString, verbose);
                if (verbose) {
                    System.out.println(String.format("Linked up DSpace id/handle %s with Elements pubs id %s",
                            dspaceString,
                            pubsString));
                }
                context.commit();
                itemsProcessed++;
            }
        } catch (SQLException | AuthorizeException e) {
            e.printStackTrace(System.err);
        } finally {
            if (context != null && context.isValid()) {
                context.abort();
            }
        }

        if (itemsProcessed > 0) {
            System.out.println(String.format("Processed %d item(s); remember to run the ListHoldings task next.", itemsProcessed));
        }
    }

    private static void processLinkup(Context context, String dspaceString, String pubsString, boolean verbose) throws SQLException, AuthorizeException {
        Item item = null;
        if (dspaceString.matches("^\\d+$")) {
            item = Item.find(context, Integer.parseInt(dspaceString));
        } else {
            DSpaceObject dso = HandleManager.resolveToObject(context, dspaceString);
            if (dso != null && dso.getType() == Constants.ITEM) {
                item = (Item) dso;
            }
        }

        if (item == null) {
            throw new IllegalArgumentException("Cannot find item with id or handle " + dspaceString);
        }

        Date now = new Date();

        TableRow pubsTableRow = DatabaseManager.querySingle(context,
                "SELECT * FROM symplectic_pids WHERE pid = ?", pubsString);
        if (pubsTableRow == null) {
            if (verbose) {
                System.out.println(String.format("Elements pid %s has no associated DSpace item", pubsString));
            }
            TableRow insertRow = new TableRow("symplectic_pids",
                    Arrays.asList("import_id", "pid", "first_imported", "last_modified", "item_id", "submission_id"));
            insertRow.setColumn("pid", pubsString);
            insertRow.setColumn("first_imported", now);
            insertRow.setColumn("last_modified", now);
            insertRow.setColumn("item_id", item.getID());
            insertRow.setColumn("submission_id", item.getID());
            DatabaseManager.insert(context, insertRow);
        } else {
            int itemId = pubsTableRow.getIntColumn("item_id");
            if (itemId == item.getID()) {
                if (verbose) {
                    System.out.println(String.format("DSpace item id %d is already linked up with Elements pid %s, not processing it further.",
                            itemId,
                            pubsString));
                }
            } else {
                int submissionId = pubsTableRow.getIntColumn("submission_id");
                if (verbose) {
                    System.out.println(String.format("DSpace item found for Elements pid %s, DSpace submission's item id is %d.",
                            pubsString,
                            submissionId));
                }
                if (submissionId != item.getID()) {
                    Item taskItem = Item.find(context, submissionId);
                    if (taskItem != null && StringUtils.isNotBlank(item.getHandle())) {
                        taskItem.addMetadata("dc", "relation", "replaces", null, item.getHandle());
                        taskItem.addMetadata("dc", "relation", "replaces", null, HandleManager.resolveToURL(context, item.getHandle()));
                        taskItem.update();
                        if (verbose) {
                            System.out.println("Updated dc.relation.replaces metadata of submission item.");
                        }
                    }
                }

                pubsTableRow.setTable("symplectic_pids");
                pubsTableRow.setColumn("item_id", item.getID());
                pubsTableRow.setColumn("last_modified", now);
                DatabaseManager.update(context, pubsTableRow);
            }
        }

        item.clearMetadata("pubs", "elements-id", null, Item.ANY);
        item.addMetadata("pubs", "elements-id", null, null, pubsString);
        item.update();
        if (verbose) {
            System.out.println("Updated pubs.elements-id metadata of existing item.");
        }
    }
}
