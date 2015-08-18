/*
 * This file is a part of the lconz-scripts project.
 * The contents of this file are subject to the license and copyright detailed in the LICENSE file at the root of the source tree.
 */

package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Script to split multi-value keywords (separated by a given delimiter string) into individual values.
 *
 * @author Andrea Schweer schweer@waikato.ac.nz for the UoW Institutional Research Repositories
 */
public class FixSquishedMetadata {
    private static final Options OPTIONS = new Options();

    static {
        Option option = new Option("f", "field", true, "The metadata field to process. Required.");
        option.setRequired(true);
        OPTIONS.addOption(option);
        option = new Option("d", "delimiter", true, "Delimiter string for squished keywords. Required.");
        option.setRequired(true);
        OPTIONS.addOption(option);
        OPTIONS.addOption("n", "dry-run", false, "If given, do not actually make any changes; instead, print out what would have been changed without this flag. Optional.");
        OPTIONS.addOption("i", "identifier", true, "Handle of DSpace object to process. If omitted, all items will be processed. Optional.");
        OPTIONS.addOption("h", "help", false, "Print help for this command and exit without taking any action.");
    }

    public static void main(String[] args) {
        CommandLine line = null;
        try {
            line = new BasicParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            System.err.println("Could not parse command line options: " + e.getMessage());
            ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
        }

        if (line == null || line.hasOption("h")) {
            ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 0, OPTIONS);
        }

        Context context = null;
        try {
            context = new Context();
            context.turnOffAuthorisationSystem();

            DSpaceObject dso = null;
            if (line.hasOption("i")) {
                String handle = line.getOptionValue("i");
                dso = HandleManager.resolveToObject(context, handle);
                if (dso == null) {
                    System.err.println("Could not resolve identifier " + handle + " to a DSpace object");
                    ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
                }
            }

            String delimiter = line.getOptionValue("d");
            boolean dryRun = line.hasOption("n");

            String schema, element, qualifier;
            String[] fieldComponents = line.getOptionValue("f", "").split("\\.");
            if (fieldComponents.length < 2) {
                System.err.println("Unsupported metadata field name: " + line.getOptionValue("f"));
                ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
            }
            schema = fieldComponents[0];
            element = fieldComponents[1];
            qualifier = fieldComponents.length > 2 ? fieldComponents[2] : null;

            boolean changes = false;
            if (dso == null || dso.getType() == Constants.SITE) {
                changes = process(Item.findByMetadataField(context, schema, element, qualifier, Item.ANY), schema, element, qualifier, delimiter, dryRun);
            } else if (dso.getType() == Constants.COMMUNITY) {
                Collection[] collections = ((Community) dso).getAllCollections();
                for (Collection collection : collections) {
                    changes |= process(collection.getAllItems(), schema, element, qualifier, delimiter, dryRun);
                }
            } else if (dso.getType() == Constants.COLLECTION) {
                changes = process(((Collection) dso).getAllItems(), schema, element, qualifier, delimiter, dryRun);
            } else if (dso.getType() == Constants.ITEM) {
                changes = process((Item) dso, schema, element, qualifier, delimiter, dryRun);
            } else {
                System.err.println("Unsupported type of DSpace object: " + dso.getTypeText() + ", need site, community, collection or item handle");
                ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
            }
            if (changes) {
                context.complete();
            }
        } catch (SQLException | AuthorizeException | IOException e) {
            e.printStackTrace(System.err);
        } finally {
            if (context != null && context.isValid()) {
                context.abort();
            }
        }
    }

    private static boolean process(ItemIterator items, String schema, String element, String qualifier, String delimiter, boolean dryRun) throws SQLException, AuthorizeException {
        boolean changes = false;
        while (items.hasNext()) {
            changes |= process(items.next(), schema, element, qualifier, delimiter, dryRun);
        }
        return changes;
    }

    private static boolean process(Item item, String schema, String element, String qualifier, String delimiter, boolean dryRun) throws SQLException, AuthorizeException {
        boolean changes = false;
        List<Metadatum> newMetadata = new ArrayList<>();

        Metadatum[] allMd = item.getMetadata(schema, element, qualifier, Item.ANY);
        for (Metadatum md : allMd) {
            if (StringUtils.isNotBlank(md.value) && md.value.contains(delimiter)) {
                String[] individualValues = StringUtils.splitByWholeSeparator(md.value, delimiter);
                for (int i = 0; i < individualValues.length; i++) {
                    individualValues[i] = individualValues[i].replaceAll("(\\r|\\n|\\t)", " ").replaceAll("  ", " ").trim();
                }
                System.out.println("item id=" + item.getID() + ": split |" + md.value + "| into |" + StringUtils.join(individualValues, '|') + "|");
                if (!dryRun) {
                    for (String individualValue : individualValues) {
                        Metadatum newMd = new Metadatum();
                        newMd.language = md.language;
                        newMd.value = individualValue;
                        newMetadata.add(newMd);
                    }
                    changes = true;
                }
            } else {
                newMetadata.add(md);
            }
        }
        if (!dryRun && changes) {
            item.clearMetadata(schema, element, qualifier, Item.ANY);
            for (Metadatum newMd : newMetadata) {
                item.addMetadata(schema, element, qualifier, newMd.language, newMd.value, newMd.authority, newMd.confidence);
            }
            item.updateMetadata();
        }
        return changes;
    }
}
