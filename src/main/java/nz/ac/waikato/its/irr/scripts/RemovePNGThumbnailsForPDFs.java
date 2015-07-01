package nz.ac.waikato.its.irr.scripts;

import org.apache.commons.lang.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 */
public class RemovePNGThumbnailsForPDFs {

	public static void main(String[] args) {
		String parentHandle = null;
		if (args.length >= 1) {
			parentHandle = args[0];
		}

		Context context = null;
		try {
			context = new Context();
			context.turnOffAuthorisationSystem();

			if (StringUtils.isBlank(parentHandle)) {
				process(context, Item.findAll(context));
			} else {
				DSpaceObject parent = HandleManager.resolveToObject(context, parentHandle);
				if (parent != null) {
					switch (parent.getType()) {
						case Constants.COLLECTION:
							process(context, ((Collection) parent).getAllItems()); // getAllItems because we want to work on non-archived ones as well
							break;
						case Constants.COMMUNITY:
							Collection[] collections = ((Community) parent).getCollections();
							for (Collection collection : collections) {
								process(context, collection.getAllItems()); // getAllItems because we want to work on non-archived ones as well
							}
							break;
						case Constants.SITE:
							process(context, Item.findAll(context));
							break;
						case Constants.ITEM:
							processItem((Item) parent);
							context.commit();
							break;
					}
				}
			}
		} catch (SQLException | AuthorizeException | IOException e) {
			e.printStackTrace(System.err);
		} finally {
			if (context != null && context.isValid()) {
				context.abort();
			}
		}
	}

	private static void process(Context context, ItemIterator items) throws SQLException, IOException, AuthorizeException {
		while (items.hasNext()) {
			Item item = items.next();
			processItem(item);
			context.commit();
			item.decache();
		}
	}

	private static void processItem(Item item) throws SQLException, AuthorizeException, IOException {
		Bundle[] thumbnailBundles = item.getBundles("THUMBNAIL");
		for (Bundle bundle : thumbnailBundles) {
			Bitstream[] bitstreams = bundle.getBitstreams();
			for (Bitstream bitstream : bitstreams) {
				if ("image/png".equals(bitstream.getFormat().getMIMEType()) && "Generated Thumbnail".equals(bitstream.getDescription())) {
					String bitstreamName = bitstream.getName();
					if (hasJpegThumbnail(thumbnailBundles, bitstreamName)) {
						bundle.removeBitstream(bitstream);
						System.out.println("Removed generated PDF thumbnail " + bitstreamName + " from item id=" + item.getID() + ", it has a new JPG thumbnail");
					}
				}
			}
		}
	}

	private static boolean hasJpegThumbnail(Bundle[] thumbnailBundles, String bitstreamName) {
		String jpgName = StringUtils.removeEndIgnoreCase(bitstreamName, ".png") + ".jpg";
		for (Bundle bundle : thumbnailBundles) {
			Bitstream candidate = bundle.getBitstreamByName(jpgName);
			if (candidate != null) {
				return true;
			}
		}
		return false;
	}
}
