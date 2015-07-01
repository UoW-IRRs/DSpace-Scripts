package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.handle.HandleManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the UoW Institutional Research Repositories
 */
public class CopyCollectionConfiguration {
	private static final Options OPTIONS = new Options();

	static {
		Option option = new Option("f", "from", true, "The handle or ID of the collection from which to copy the configuration. Required.");
		option.setRequired(true);
		OPTIONS.addOption(option);
		option = new Option("t", "to", true, "Handle or ID of the collection to which to copy the configuration. Can be specified multiple times to process multiple collections. At least one is required.");
		option.setRequired(true);
		OPTIONS.addOption(option);
		option = new Option("c", "components", true, "The component to copy. Can be specified multiple times to copy multiple components, but at least one is required. Available components are almstw for a=administrators, l=logo, m=metadata, s=submitters, t=template, w=workflow.");
		option.setRequired(true);
		OPTIONS.addOption(option);
		OPTIONS.addOption("h", "help", false, "Print help for this command and exit without taking any action.");
	}

	public static void main(String[] args) {
		CommandLine line = null;
		try {
			line = new BasicParser().parse(OPTIONS, args);
		} catch (ParseException e) {
			System.err.println("Could not parse command line options: " + e.getMessage());
			ScriptUtils.printHelpAndExit(CopyCollectionConfiguration.class.getSimpleName(), 1, OPTIONS);
		}

		// TODO option to copy auth policies?

		if (line.hasOption("h")) {
			ScriptUtils.printHelpAndExit(CopyCollectionConfiguration.class.getSimpleName(), 0, OPTIONS);
		}

		if (!line.hasOption("f") || !line.hasOption("t")) {
			System.err.println("Both 'to' and 'from' option are required.");
			ScriptUtils.printHelpAndExit(CopyCollectionConfiguration.class.getSimpleName(), 1, OPTIONS);
		}

		if (!line.hasOption("c")) {
			System.err.println("At least one component is required.");
			ScriptUtils.printHelpAndExit(CopyCollectionConfiguration.class.getSimpleName(), 1, OPTIONS);
		}

		List<String> components = Arrays.asList(line.getOptionValues("c"));
		if (components.isEmpty()) {
			System.err.println("At least one component is required.");
			ScriptUtils.printHelpAndExit(CopyCollectionConfiguration.class.getSimpleName(), 1, OPTIONS);
		}

		Context context = null;
		try {
			context = new Context();
			context.turnOffAuthorisationSystem();


			String fromString = line.getOptionValue("f");
			List<Collection> fromCollectionList = findCollections(context, fromString);
			if (fromCollectionList == null || fromCollectionList.isEmpty() || fromCollectionList.get(0) == null) {
				System.err.println("Could not find collection from which to copy, handle or ID was " + fromString);
				return;
			}
			Collection fromCollection = fromCollectionList.get(0);

			String[] toStrings = line.getOptionValues("t");
			for (String toString : toStrings) {
				List<Collection> toCollections = findCollections(context, toString);
				for (Collection toCollection : toCollections) {
					processCollection(context, fromCollection, toCollection, components);
				}
			}
		} catch (SQLException | AuthorizeException | IOException e) {
			e.printStackTrace(System.err);
		} finally {
			// clean up if necessary
			if (context != null && context.isValid()) {
				context.abort();
			}
		}
	}

	private static void processCollection(Context context, Collection fromCollection, Collection toCollection, List<String> components) throws SQLException, AuthorizeException, IOException {
		if (components.contains("m")) {
			copyMetadata(fromCollection, toCollection);
		}
		if (components.contains("l")) {
			copyLogo(fromCollection, toCollection);
		}

		if (components.contains("a")) {
			copyAdministrators(fromCollection, toCollection);
		}

		if (components.contains("s")) {
			copySubmitters(fromCollection, toCollection);
		}

		if (components.contains("w")) {
			copyWorkflowRoles(fromCollection, toCollection, 1);
			copyWorkflowRoles(fromCollection, toCollection, 2);
			copyWorkflowRoles(fromCollection, toCollection, 3);
		}

		if (components.contains("t")) {
			copyTemplate(fromCollection, toCollection);
		}
		toCollection.update();
		// default read policy?
		context.commit();
	}

	private static void copyLogo(Collection fromCollection, Collection toCollection) throws SQLException, IOException, AuthorizeException {
		Bitstream logoStream = fromCollection.getLogo();
		if (logoStream == null) {
			toCollection.setLogo(null);
		} else {
			toCollection.setLogo(logoStream.retrieve());
		}
	}

	private static void copyTemplate(Collection fromCollection, Collection toCollection) throws SQLException, IOException, AuthorizeException {
		Item fromTemplate = fromCollection.getTemplateItem();
		toCollection.removeTemplateItem();
		if (fromTemplate != null) {
			toCollection.createTemplateItem();
			Item toTemplate = toCollection.getTemplateItem();
			copyMetadata(fromTemplate, toTemplate);
		}
	}

	private static void copyAdministrators(Collection fromCollection, Collection toCollection) throws SQLException, AuthorizeException {
		Group fromAdministrators = fromCollection.getAdministrators();

		// Todo remove auth policies of old group?

		// remove "to" collections current admin group, deleting it if it is the default admin group
		Group oldToAdministrators = toCollection.getAdministrators();
		if (oldToAdministrators != null) {
			toCollection.removeAdministrators();
			toCollection.update();
			if (oldToAdministrators.getName().equalsIgnoreCase("COLLECTION_" + toCollection.getID() + "_ADMIN")) {
				oldToAdministrators.delete();
			}
		}

		if (fromAdministrators != null) {
			// create new default admin group for "to" collection
			Group newToAdministrators = toCollection.createAdministrators();
			// copy all members of "from" collection's admin group to "to" collection's admin group
			Group[] fromAdminGroups = fromAdministrators.getMemberGroups();
			for (Group group : fromAdminGroups) {
				newToAdministrators.addMember(group);
			}
			EPerson[] fromAdminEpeople = fromAdministrators.getMembers();
			for (EPerson ePerson : fromAdminEpeople) {
				newToAdministrators.addMember(ePerson);
			}
			newToAdministrators.update();
		}
	}

	private static void copySubmitters(Collection fromCollection, Collection toCollection) throws SQLException, AuthorizeException {
		Group fromSubmitters = fromCollection.getSubmitters();

		// Todo remove auth policies of old group?

		Group oldToSubmitters = toCollection.getSubmitters();
		if (oldToSubmitters != null) {
			toCollection.removeSubmitters();
			toCollection.update();
			if (oldToSubmitters.getName().equalsIgnoreCase("COLLECTION_" + toCollection.getID() + "_SUBMIT")) {
				oldToSubmitters.delete();
			}
		}

		if (fromSubmitters != null) {
			Group newToSubmitters = toCollection.createSubmitters();
			Group[] fromSubmitterGroups = fromSubmitters.getMemberGroups();
			for (Group group : fromSubmitterGroups) {
				newToSubmitters.addMember(group);
			}
			EPerson[] fromSubmitterEPeople = fromSubmitters.getMembers();
			for (EPerson ePerson : fromSubmitterEPeople) {
				newToSubmitters.addMember(ePerson);
			}
			newToSubmitters.update();
		}
	}

	private static void copyWorkflowRoles(Collection fromCollection, Collection toCollection, int step) throws SQLException, AuthorizeException {
		Group fromRole = fromCollection.getWorkflowGroup(step);

		// Todo remove auth policies of old group?

		Group oldToRole = toCollection.getWorkflowGroup(step);
		if (oldToRole != null) {
			toCollection.setWorkflowGroup(step, null);
			toCollection.update();
			if (oldToRole.getName().equalsIgnoreCase("COLLECTION_" + toCollection.getID() + "_WORKFLOW_STEP_" + step)) {
				oldToRole.delete();
			}
		}

		if (fromRole != null) {
			Group newToRole = toCollection.createWorkflowGroup(step);
			Group[] fromRoleGroups = fromRole.getMemberGroups();
			for (Group group : fromRoleGroups) {
				newToRole.addMember(group);
			}
			EPerson[] fromRoleEpeople = fromRole.getMembers();
			for (EPerson ePerson : fromRoleEpeople) {
				newToRole.addMember(ePerson);
			}
			newToRole.update();
		}
	}

	private static void copyMetadata(DSpaceObject from, DSpaceObject to) throws SQLException, AuthorizeException {
		Metadatum[] fromMetadata = from.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY);
		to.clearMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY);
		for (Metadatum fromMd : fromMetadata) {
			to.addMetadata(fromMd.schema, fromMd.element, fromMd.qualifier, fromMd.language, fromMd.value, fromMd.authority, fromMd.confidence);
		}
		to.update();
	}

	private static List<Collection> findCollections(Context context, String identifierString) throws SQLException {
		List<Collection> result = new ArrayList<>();
		if (identifierString.matches("^\\d+$")) {
			int collectionId = Integer.parseInt(identifierString);
			Collection collection = Collection.find(context, collectionId);
			if (collection != null) {
				result.add(collection);
			}
		}
		if (identifierString.matches("^\\d+/\\d+$")) {
			DSpaceObject dso = HandleManager.resolveToObject(context, identifierString);
			if (dso != null) {
				if (dso instanceof Collection) {
					result.add((Collection) dso);
				} else if (dso instanceof Community) {
					Community parent = (Community) dso;
					Collections.addAll(result, parent.getAllCollections());
				}
			}
		}
		return result;
	}
}
