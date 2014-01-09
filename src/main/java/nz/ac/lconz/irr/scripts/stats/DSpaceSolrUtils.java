package nz.ac.lconz.irr.scripts.stats;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class DSpaceSolrUtils {


	public static final DateFormat SOLR_DATE_FORMAT;
	static {
		SOLR_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		SOLR_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public static CommonsHttpSolrServer setupSolrServer(String solrServerUrl) {
		CommonsHttpSolrServer solr = null;
		try {
			solr = new CommonsHttpSolrServer(solrServerUrl);
			SolrQuery solrQuery = new SolrQuery().setQuery("type:2 AND id:1");
			solr.query(solrQuery);
		} catch (Exception e) {
			System.err.println("Cannot connect to solr server -- path given is " + solrServerUrl);
			e.printStackTrace(System.err);
		}

		if (solr == null) {
			System.err.println("Cannot connect to solr server -- path given is " + solrServerUrl);
		}
		return solr;
	}
}
