package nz.ac.lconz.irr.scripts.stats;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.joda.time.DateTime;
import org.joda.time.Minutes;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class CountPagesBeforeDownload {

	private CommonsHttpSolrServer solrServer;
	private Date toTimestamp;
	private Date fromTimestamp;
	private Map<String, Integer> pathCounts = new HashMap<String, Integer>();
	private static final Minutes IDLE_CUTOFF = Minutes.TWO;

	public CountPagesBeforeDownload(CommonsHttpSolrServer solrServer, Date fromTimestamp, Date toTimestamp){
		this.solrServer = solrServer;
		this.fromTimestamp = fromTimestamp;
		this.toTimestamp = toTimestamp;
	}

	public static void main(String[] argv) {
		if (argv.length < 1) {
			System.out.println("Usage: CountPagesBeforeDownload solrServer [fromTimestamp] [toTimestamp]");
			System.exit(1);
		}

		CommonsHttpSolrServer solrServer = DSpaceSolrUtils.setupSolrServer(argv[0]);
		if (solrServer == null) {
			System.exit(1);
		}

		Date fromTimestamp = null;
		Date toTimestamp = null;

		if (argv.length > 1) {
			String fromTimestampString = argv[1];
			try {
				fromTimestamp = DSpaceSolrUtils.SOLR_DATE_FORMAT.parse(fromTimestampString);
			} catch (ParseException e) {
				System.err.println("cannot parse fromTimestamp, expected format is " + DSpaceSolrUtils.SOLR_DATE_FORMAT.toString());
				e.printStackTrace(System.err);
				System.exit(1);
			}

			if (argv.length > 2) {
				String toTimestampString = argv[2];
				try {
					toTimestamp = DSpaceSolrUtils.SOLR_DATE_FORMAT.parse(toTimestampString);
				} catch (ParseException e) {
					System.err.println("cannot parse toTimestamp, expected format is " + DSpaceSolrUtils.SOLR_DATE_FORMAT.toString());
					e.printStackTrace(System.err);
					System.exit(1);
				}
			}
		}

		new CountPagesBeforeDownload(solrServer, fromTimestamp, toTimestamp).run();
	}

	private void run() {
		StringBuilder queryString = new StringBuilder("(type:0 OR type:2)");
		if (fromTimestamp != null) {
			queryString.append(" AND time:{");
			queryString.append(DSpaceSolrUtils.SOLR_DATE_FORMAT.format(fromTimestamp));
			queryString.append(" TO ");
			if (toTimestamp != null) {
				queryString.append(DSpaceSolrUtils.SOLR_DATE_FORMAT.format(toTimestamp));
				queryString.append("}");
			} else {
				queryString.append("*}");
			}
		}
		SolrQuery query = new SolrQuery(queryString.toString());
		query.setFacet(true);
		query.addFacetField("ip");
		query.addFilterQuery("-isBot:true");
		query.setRows(0);

		try {
			FacetField ipAddresses = solrServer.query(query).getFacetField("ip");
			for (FacetField.Count count : ipAddresses.getValues()) {
				processIPAddress(count.getAsFilterQuery());
			}
		} catch (SolrServerException e) {
			e.printStackTrace(System.err);
		}
		printResults();
	}

	private void printResults() {
		System.out.println("Results");
		for (String path : pathCounts.keySet()) {
			System.out.format("%s\t%d\n", path, pathCounts.get(path));
		}
	}


	private void processIPAddress(String ipFilter) {
		StringBuilder queryString = new StringBuilder("(type:0 OR type:2 OR type:3 OR type:4 OR type:5)");

		SolrQuery query = new SolrQuery(queryString.toString());
		query.addFilterQuery(ipFilter);
		query.addFilterQuery("-isBot:true");
		query.addSortField("time", SolrQuery.ORDER.asc);
		query.setRows(5000);
		query.addField("id");
		query.addField("type");
		query.addField("time");
		query.addField("userAgent");
		query.addField("ip");

		DateTime mostRecentTimestamp = null;
		String mostRecentUserAgent = null;
		StringBuilder path = new StringBuilder();
		try {
			SolrDocumentList hits = solrServer.query(query).getResults();
			for (SolrDocument doc : hits) {
				DateTime time = new DateTime(doc.getFieldValue("time"));
				String userAgent = (String) doc.getFieldValue("userAgent");
				String ip = (String) doc.getFieldValue("ip");

				if (!isSameVisit(mostRecentTimestamp, time, mostRecentUserAgent, userAgent)) {
					System.out.format("%s\t%s\t%s\n", path, ip, mostRecentUserAgent);
					incrementPathCount(path.toString());
					path = new StringBuilder();
				}
				mostRecentTimestamp = time;
				mostRecentUserAgent = userAgent;

				appendPathComponent(path, doc);
			}
		} catch (SolrServerException e) {
			e.printStackTrace(System.err);
		}
	}

	private boolean isSameVisit(DateTime mostRecentTimestamp, DateTime time, String mostRecentUserAgent, String userAgent) {
		boolean withinTimeLimit = mostRecentTimestamp == null || !Minutes.minutesBetween(mostRecentTimestamp, time).isGreaterThan(IDLE_CUTOFF);
		boolean sameUserAgent = (mostRecentUserAgent == null && userAgent == null) || (mostRecentUserAgent != null && userAgent!= null && mostRecentUserAgent.equals(userAgent));
		return withinTimeLimit && sameUserAgent;
	}

	private void appendPathComponent(StringBuilder path, SolrDocument doc) {
		int type = (Integer) doc.getFieldValue("type");
		switch (type) {
			case 0:
				path.append("F"); // File
				break;
			case 2:
				path.append("I"); // Item
				break;
			case 3:
				path.append("L"); //  coLLection
				break;
			case 4:
				path.append("M"); // coMMunity
				break;
			case 5:
				path.append("S"); // Site
				break;
		}
	}

	private void incrementPathCount(String path) {
		if (path == null || "".equals(path)) {
			return; // do nothing for empty string
		}
		int count = pathCounts.containsKey(path) ? pathCounts.get(path) : 0;
		pathCounts.put(path, ++count);
	}
}
