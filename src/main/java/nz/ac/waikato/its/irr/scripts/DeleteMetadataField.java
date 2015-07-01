package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.workflow.WorkflowItem;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz, University of Waikato ITS ISG
 */
public class DeleteMetadataField {

	public static final Options OPTIONS = new Options();
	static {
		OPTIONS.addOption("n", "dry-run", false, "Don't actually make any changes, just report on what would be done.");
		Option fieldOption = new Option("f", "field", true, "The metadata field whose values should be deleted. Must follow the pattern schema.element.qualifier or schema.element.");
		fieldOption.setRequired(true);
		OPTIONS.addOption(fieldOption);
		OPTIONS.addOption("r", "registry", false, "Also remove the field from the metadata registry. This will fail if any withdrawn items or template items contain this metadata field.");
		OPTIONS.addOption("h", "help", false, "Print help for this command.");
	}

	public static void main(String[] args) {
		try {
			CommandLine line = new BasicParser().parse(OPTIONS, args);
			if (line.hasOption("h")) {
				ScriptUtils.printHelpAndExit(DeleteMetadataField.class.getSimpleName(), 0, OPTIONS);
			}

			if (!line.hasOption("f")) {
				System.err.println("Field option is required but wasn't give.");
				ScriptUtils.printHelpAndExit(DeleteMetadataField.class.getSimpleName(), 1, OPTIONS);
			}

			String field = line.getOptionValue("f");
			if (StringUtils.isBlank(field)) {
				System.err.println("A metadata field must be specified using the field option.");
				ScriptUtils.printHelpAndExit(DeleteMetadataField.class.getSimpleName(), 1, OPTIONS);
			}
			String[] fieldComponents = field.split("\\.");
			if (fieldComponents.length < 2) {
				System.err.println("Metadata field given via field option must follow pattern schema.element.qualifier or schema.element");
				ScriptUtils.printHelpAndExit(DeleteMetadataField.class.getSimpleName(), 1, OPTIONS);
			}
            String schema = fieldComponents[0];
			String element = fieldComponents[1];
			String qualifier = fieldComponents.length > 2 ? fieldComponents[2] : null;

			Context context = null;
			try {
				context = new Context();
				context.turnOffAuthorisationSystem();

				ItemIterator affectedItems = Item.findByMetadataField(context, schema, element, qualifier, Item.ANY);
				while (affectedItems.hasNext()) {
					Item item = affectedItems.next();
					if (line.hasOption("n")) {
						System.out.println("Dry run, not deleting metadata values for item_id=" + item.getID() + ", field=" + field);
						Metadatum[] values = item.getMetadata(schema, element, qualifier, Item.ANY);
						for (Metadatum value : values) {
							System.out.print("\t" + value.value);
							if (StringUtils.isNotBlank(value.authority)) {
								System.out.print(", authority=" + value.authority);
							}
							System.out.println();
						}
					} else {
						item.clearMetadata(schema, element, qualifier, Item.ANY);
						item.update();
					}
					item.decache();
				}
				context.commit();

				WorkflowItem[] workflowItems = WorkflowItem.findAll(context);
				for (WorkflowItem wfItem : workflowItems) {
					Item item = wfItem.getItem();
					Metadatum[] values = item.getMetadata(schema, element, qualifier, Item.ANY);
					if (values == null || values.length == 0) {
						item.decache();
						continue;
					}
					if (line.hasOption("n")) {
						System.out.println("Dry run, not deleting metadata values for workflow item item_id=" + item.getID() + ", field=" + field);
						for (Metadatum value : values) {
							System.out.print("\t" + value.value);
							if (StringUtils.isNotBlank(value.authority)) {
								System.out.print(", authority=" + value.authority);
							}
							System.out.println();
						}
					} else {
						item.clearMetadata(schema, element, qualifier, Item.ANY);
						item.update();
					}
					item.decache();
				}
				context.commit();

				WorkspaceItem[] workspaceItems = WorkspaceItem.findAll(context);
				for (WorkspaceItem wsItem : workspaceItems) {
					Item item = wsItem.getItem();
					Metadatum[] values = item.getMetadata(schema, element, qualifier, Item.ANY);
					if (values == null || values.length == 0) {
						item.decache();
						continue;
					}
					if (line.hasOption("n")) {
						System.out.println("Dry run, not deleting metadata values for workflow item item_id=" + item.getID() + ", field=" + field);
						for (Metadatum value : values) {
							System.out.print("\t" + value.value);
							if (StringUtils.isNotBlank(value.authority)) {
								System.out.print(", authority=" + value.authority);
							}
							System.out.println();
						}
					} else {
						item.clearMetadata(schema, element, qualifier, Item.ANY);
						item.update();
					}
					item.decache();
				}
				context.commit();

				if (line.hasOption("r")) {
					if (line.hasOption("n")) {
						System.out.println("Dry run, not deleting field " + field + " from metadata registry");
					} else {
						try {
							MetadataSchema registrySchema = MetadataSchema.find(context, schema);
							MetadataField registryField = MetadataField.findByElement(context, registrySchema.getSchemaID(), element, qualifier);
							registryField.delete(context);
							context.commit();
						} catch (Exception e) {
							System.err.println("Could not delete field " + field + " from metadata registry; check withdrawn items and template items.");
							System.err.println(e.getMessage());
						}
					}
				}
				context.complete();
			} catch (SQLException | AuthorizeException | IOException e) {
				e.printStackTrace(System.err);
			} finally {
				if (context != null && context.isValid()) {
					context.abort();
				}
			}
		} catch (ParseException e) {
			System.err.println("Could not parse arguments: " + e.getMessage());
			ScriptUtils.printHelpAndExit(DeleteMetadataField.class.getSimpleName(), 1, OPTIONS);
		}
	}

}
