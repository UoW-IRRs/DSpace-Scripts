/*
 * This file is a part of the lconz-scripts project.
 * The contents of this file are subject to the license and copyright detailed in the LICENSE file at the root of the source tree.
 */

package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.*;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *         for the University of Waikato's Institutional Research Repositories
 */
public class MoveMetadataValues {
    private static final Options OPTIONS = new Options();

    static {
        Option option = new Option("s", "source", true, "The source metadata field. Required.");
        option.setRequired(true);
        OPTIONS.addOption(option);
        option = new Option("t", "target", true, "The target metadata field. Required.");
        option.setRequired(true);
        OPTIONS.addOption(option);
        OPTIONS.addOption("l", "language", true, "The desired language for the metadata field. Optional. If omitted, the metadata language will be unchanged.");
        OPTIONS.addOption("r", "restrict-values", true, "Name of a file that contains specific values that should be processed, one per line. Optional. If omitted, all values of the given field will have their metadata language changed.");
        OPTIONS.addOption("n", "dry-run", false, "If given, do not actually make any changes; instead, print out what would have been changed without this flag. Optional.");
        OPTIONS.addOption("i", "identifier", true, "Handle of DSpace object to process. If omitted, all items will be processed. Optional.");
        OPTIONS.addOption("c", "case-sensitive", false, "If given and -r is present, use case sensitive matching. Optional. If omitted, case insensitive matching is used.");
        OPTIONS.addOption("p", "preferred", false, "If given and -r is present but -c isn't, use the capitalisation as given in the file specified by -r for the new metadata value. If omitted, the new metadata value will use the same capitalisation as the old one.");
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
            return;
        }

        boolean dryRun = line.hasOption("n");

        String sourceSchema, sourceElement, sourceQualifier;
        String[] sourceFieldComponents = line.getOptionValue("s", "").split("\\.");
        if (sourceFieldComponents.length < 2) {
            System.err.println("Unsupported source metadata field name: " + line.getOptionValue("s"));
            ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
        }
        sourceSchema = sourceFieldComponents[0];
        sourceElement = sourceFieldComponents[1];
        sourceQualifier = sourceFieldComponents.length > 2 ? sourceFieldComponents[2] : null;

        String targetSchema, targetElement, targetQualifier;
        String[] targetFieldComponents = line.getOptionValue("t", "").split("\\.");
        if (targetFieldComponents.length < 2) {
            System.err.println("Unsupported target metadata field name: " + line.getOptionValue("t"));
            ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
        }
        targetSchema = targetFieldComponents[0];
        targetElement = targetFieldComponents[1];
        targetQualifier = targetFieldComponents.length > 2 ? targetFieldComponents[2] : null;

        String language = line.getOptionValue("l");

        boolean matchCase = line.hasOption("c");
        boolean usePreferredCase = line.hasOption("p");

        Map<String, String> valuesFilter = new HashMap<>();
        if (line.hasOption("r")) {
            File valuesFile = new File(line.getOptionValue("r"));
            if (valuesFile.exists() && valuesFile.canRead()) {
                try (Scanner scanner = new Scanner(valuesFile)) {
                    while (scanner.hasNextLine()) {
                        String value = scanner.nextLine().trim();
                        String key = matchCase ? value : value.toLowerCase();
                        valuesFilter.put(key, value);
                    }
                } catch (FileNotFoundException e) {
                    System.err.println("Problem reading values file " + line.getOptionValue("r") + ": " + e.getMessage());
                    ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
                }
            } else {
                System.err.println("Values file " + line.getOptionValue("r") + " doesn't exist or is not readable, aborting");
                ScriptUtils.printHelpAndExit(FixSquishedMetadata.class.getSimpleName(), 1, OPTIONS);
            }
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

            boolean changes = false;
            if (dso == null || dso.getType() == Constants.SITE) {
                changes = process(Item.findByMetadataField(context, sourceSchema, sourceElement, sourceQualifier, Item.ANY), sourceSchema, sourceElement, sourceQualifier, targetSchema, targetElement, targetQualifier, language, valuesFilter, matchCase, usePreferredCase, dryRun);
            } else if (dso.getType() == Constants.COMMUNITY) {
                Collection[] collections = ((Community) dso).getAllCollections();
                for (Collection collection : collections) {
                    changes |= process(collection.getAllItems(), sourceSchema, sourceElement, sourceQualifier, targetSchema, targetElement, targetQualifier, language, valuesFilter, matchCase, usePreferredCase, dryRun);
                }
            } else if (dso.getType() == Constants.COLLECTION) {
                changes = process(((Collection) dso).getAllItems(), sourceSchema, sourceElement, sourceQualifier, targetSchema, targetElement, targetQualifier, language, valuesFilter, matchCase, usePreferredCase, dryRun);
            } else if (dso.getType() == Constants.ITEM) {
                changes = process((Item) dso, sourceSchema, sourceElement, sourceQualifier, targetSchema, targetElement, targetQualifier, language, valuesFilter, matchCase, usePreferredCase, dryRun);
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

    private static boolean process(ItemIterator items, String schema, String element, String qualifier, String targetSchema, String targetElement, String targetQualifier, String language, Map<String, String> valuesFilter, boolean matchCase, boolean usePreferredCase, boolean dryRun) throws SQLException, AuthorizeException {
        boolean changes = false;
        while (items.hasNext()) {
            changes |= process(items.next(), schema, element, qualifier, targetSchema, targetElement, targetQualifier, language, valuesFilter, matchCase, usePreferredCase, dryRun);
        }
        return changes;
    }

    private static boolean process(Item item, String sourceSchema, String sourceElement, String sourceQualifier, String targetSchema, String targetElement, String targetQualifier, String language, Map<String, String> valuesFilter, boolean matchCase, boolean usePreferredCase, boolean dryRun) throws SQLException, AuthorizeException {
        boolean changes = false;
        List<Metadatum> retainSourceMetadata = new ArrayList<>();
        List<Metadatum> addTargetMetadata = new ArrayList<>();

        Metadatum[] currentSourceMd = item.getMetadata(sourceSchema, sourceElement, sourceQualifier, Item.ANY);
        for (Metadatum md : currentSourceMd) {
            String key = matchCase ? md.value : md.value.toLowerCase();
            if (StringUtils.isNotBlank(md.value) && (valuesFilter.isEmpty() || valuesFilter.containsKey(key))) {
                String newValue = usePreferredCase ? valuesFilter.get(key) : md.value;
                System.out.println("item id=" + item.getID() + ": moving value |" + md.value + "| from field "
                        + StringUtils.join(new String[] {sourceSchema, sourceElement, sourceQualifier}, ".")
                        + " to |" + newValue + "| field "
                        + StringUtils.join(new String[] {targetSchema, targetElement, targetQualifier}, "."));
                if (!dryRun) {
                    Metadatum newMd = new Metadatum();
                    newMd.language = StringUtils.isNotBlank(language) ? language : md.language;
                    newMd.value = newValue;
                    newMd.authority = md.authority;
                    newMd.confidence = md.confidence;
                    addTargetMetadata.add(newMd);
                }
                changes = true;
            } else {
                // just leave value as is
                retainSourceMetadata.add(md);
            }
        }
        if (!dryRun && changes) {
            item.clearMetadata(sourceSchema, sourceElement, sourceQualifier, Item.ANY);
            for (Metadatum newMd : retainSourceMetadata) {
                item.addMetadata(sourceSchema, sourceElement, sourceQualifier, newMd.language, newMd.value, newMd.authority, newMd.confidence);
            }
            for (Metadatum newMd : addTargetMetadata) {
                item.addMetadata(targetSchema, targetElement, targetQualifier, newMd.language, newMd.value, newMd.authority, newMd.confidence);
            }
            item.updateMetadata();
        }
        item.decache();
        return changes;
    }
}
