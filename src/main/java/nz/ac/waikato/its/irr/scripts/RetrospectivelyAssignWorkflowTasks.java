/*
 * This file is a part of the lconz-scripts project.
 * The contents of this file are subject to the license and copyright detailed in the LICENSE file at the root of the source tree.
 */

package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.cli.*;
import org.dspace.content.Collection;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *         for the University of Waikato's Institutional Research Repositories
 */
public class RetrospectivelyAssignWorkflowTasks {
    private static final Options OPTIONS = new Options();

    static {
        Option option = new Option("e", "email", true, "The e-mail address of the person who should have workflow tasks retrospectively assigned. Required.");
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
            ScriptUtils.printHelpAndExit(RetrospectivelyAssignWorkflowTasks.class.getSimpleName(), 1, OPTIONS);
        }

        if (line == null || line.hasOption("h")) {
            ScriptUtils.printHelpAndExit(RetrospectivelyAssignWorkflowTasks.class.getSimpleName(), 0, OPTIONS);
        }
        if (!line.hasOption('e')) {
            System.err.println("Missing required parameter (e-mail address)");
            ScriptUtils.printHelpAndExit(RetrospectivelyAssignWorkflowTasks.class.getSimpleName(), 1, OPTIONS);
        }
        String email = line.getOptionValue('e');

        Context context = null;
        try {
            context = new Context();
            EPerson ePerson = EPerson.findByEmail(context, email);
            if (ePerson == null) {
                System.err.println("Couldn't find an account for e-mail address " + email);
                context.abort();
                return;
            }

            List<WorkflowItem> alreadyAccessibleTasks = new ArrayList<>();
            alreadyAccessibleTasks.addAll(WorkflowManager.getOwnedTasks(context, ePerson));
            alreadyAccessibleTasks.addAll(WorkflowManager.getPooledTasks(context, ePerson));

            WorkflowItem[] allTasks = WorkflowItem.findAll(context);
            for (WorkflowItem task : allTasks) {
                System.out.println("Processing task " + task.getID());
                if (alreadyAccessibleTasks.contains(task)) {
                    System.out.println("EPerson already has access to this task");
                    continue;
                }

                Collection collection = task.getCollection();
                boolean ePersonShouldSeeTask = checkIsMember(context, collection.getWorkflowGroup(1), ePerson)
                        || checkIsMember(context, collection.getWorkflowGroup(2), ePerson)
                        || checkIsMember(context, collection.getWorkflowGroup(3), ePerson);

                if (!ePersonShouldSeeTask) {
                    System.out.println("EPerson isn't in any of the workflow groups for this task");
                    continue;
                }

                System.out.println("Giving EPerson " + email + " access to workflow task " + task.getID());

                TableRow tr = DatabaseManager.row("tasklistitem");
                tr.setColumn("eperson_id", ePerson.getID());
                tr.setColumn("workflow_id", task.getID());
                DatabaseManager.insert(context, tr);
            }

            context.commit();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            if (context != null && context.isValid()) {
                context.abort();
            }
        }
    }

    private static boolean checkIsMember(Context context, Group group, EPerson ePerson) throws SQLException {
        if (group == null) {
            return false;
        }
        EPerson[] allMembers = Group.allMembers(context, group);
        return Arrays.asList(allMembers).contains(ePerson);
    }

}
