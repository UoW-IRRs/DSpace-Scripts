package nz.ac.lconz.irr.scripts.sync;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class RemoveNonPublicItems {

	public static void main(String[] args) {
		Context context = null;
		try {
			context = new Context();
			context.turnOffAuthorisationSystem();

			ItemIterator items = Item.findAll(context);
			while (items.hasNext()) {
				Item item = items.next();
				try {
					processItem(item);
				} catch (AuthorizeException e) {
					e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
				} catch (IOException e) {
					e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
				}
			}
			context.commit();
			context = null;
		} catch (SQLException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		} finally {
			if (context != null) {
				context.abort();
			}
		}
	}

	private static void processItem(Item item) throws SQLException, AuthorizeException, IOException {
		if (!item.isArchived() || item.isWithdrawn()) {
			// don't do anything to items that aren't publicly visible anyway
			return;
		}
		if (!anonymousCanRead(item)) {
			Collection owningCollection = item.getOwningCollection();

			// remove from mapped collections
			Collection[] itemCollections = item.getCollections();
			for (Collection coll : itemCollections) {
				if (coll.getID() != owningCollection.getID()) {
					coll.removeItem(item);
				}
			}
			// remove from owning collection this will also delete the item since the owning collection holds the last reference to the item
			owningCollection.removeItem(item);
		}
	}

	private static boolean anonymousCanRead(Item item) {
		Context context = null;
		try {
			context = new Context();
			return AuthorizeManager.authorizeActionBoolean(context, item, Constants.READ);
		} catch (SQLException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		} finally {
			if (context != null) {
				context.abort();
			}
		}
		return false;
	}
}
