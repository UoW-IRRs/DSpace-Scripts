package nz.ac.lconz.irr.scripts.stats;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class UpdateContainerInfo {
	public static void main(String[] argv) throws SQLException {
		String parentHandle = null;
		if (argv.length >= 1) {
			parentHandle = argv[0];
		}
		Context context = null;
		try {
			context = new Context();
			context.turnOffAuthorisationSystem();

			if (StringUtils.isBlank(parentHandle)) {
				process(Item.findAll(context));
			} else {
				DSpaceObject dso = HandleManager.resolveToObject(context, parentHandle);
				if (dso == null) {
					System.err.printf("Cannot resolve supplied handle %s to collection or community\n", parentHandle);
					System.exit(1);
				}
				if (dso instanceof Collection) {
					Collection collection = (Collection) dso;
					process(collection.getAllItems());
				} else if (dso instanceof Community) {
					Community community = (Community) dso;
					Collection[] children = community.getCollections();
					for (Collection child : children) {
						process(child.getAllItems());
					}
				} else {
					System.err.printf("Object with supplied handle %s is not a collection or community; not processing any items\n", parentHandle);
					System.exit(1);
				}
			}
		} finally {
			if (context != null && context.isValid()) {
				context.abort();
			}
		}

	}

	private static void process(ItemIterator items) throws SQLException {
		while (items.hasNext()) {
			Item item = items.next();
			Collection[] collections = item.getCollections();
			Community[] communities = item.getCommunities();

			item.decache();
		}
	}
}
