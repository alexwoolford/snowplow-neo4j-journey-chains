package io.woolford.snowplow.neo4j;

import com.google.common.util.concurrent.AtomicLongMap;
import io.woolford.snowplow.neo4j.result.AppendToJourneyChainResult;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.util.Preconditions;

import java.util.HashMap;
import java.util.stream.Stream;

public class AppendToJourneyChain {

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    @Procedure(value = "snowplow.append.journey.chain", mode = Mode.WRITE)
    @Description("snowplow.append.journey.chain(domain_userid, page_url, timestamp)")
    public Stream<AppendToJourneyChainResult> snowplowChainAppend(@Name("domain_userid") String domainUserid, @Name("page_url") String pageUrl, @Name("timestamp") long timestamp) {

        Preconditions.checkArgument(domainUserid != null, "domain_userid cannot be null");
        Preconditions.checkArgument(pageUrl != null, "page_url cannot be null");
        Preconditions.checkArgument(timestamp > 0, "timestamp must be greater than 0; was ", timestamp);

        AtomicLongMap<String> counters = AtomicLongMap.create(new HashMap<String, Long>());

        try {
            Transaction tx = db.beginTx();

            Node pageView = tx.createNode(Label.label("PageView"));
            pageView.setProperty("page_url", pageUrl);
            pageView.setProperty("timestamp", timestamp);

            // merge the user
            Node userNode = tx.findNode(Label.label("User"), "domain_userid", domainUserid);
            if (userNode == null) {
                userNode = tx.createNode(Label.label("User"));
                userNode.setProperty("domain_userid", domainUserid);
            }

            // create HAS_CHAIN relationship between user and pageView if no
            boolean firstPageView;
            if (!userNode.hasRelationship(RelationshipType.withName("HAS_CHAIN"))){
                firstPageView = true;
                userNode.createRelationshipTo(pageView, RelationshipType.withName("HAS_CHAIN"));
                counters.incrementAndGet("chainLength");
            } else {
                firstPageView = false;
            }

            // follow the chain to the end, and then add the pageView node to the end of the chain
            Node node = userNode.getSingleRelationship(RelationshipType.withName("HAS_CHAIN"), Direction.OUTGOING).getEndNode(); // first pageView node
            while (node.hasRelationship(Direction.OUTGOING, RelationshipType.withName("NEXT"))) {
                node = node.getSingleRelationship(RelationshipType.withName("NEXT"), Direction.OUTGOING).getEndNode();
                counters.incrementAndGet("chainLength");
            }

            if (!firstPageView){
                node.createRelationshipTo(pageView, RelationshipType.withName("NEXT"));
            }

            tx.commit();

            log.info("Added PageView " + pageView.getAllProperties() + " for User " + userNode.getAllProperties());

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return Stream.of(new AppendToJourneyChainResult(counters));

    }

}
