package io.woolford.snowplow.neo4j;

import com.google.common.util.concurrent.AtomicLongMap;
import io.woolford.snowplow.neo4j.result.CreateJourneyChainsResult;
import org.javatuples.Pair;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateJourneyChains {

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    @Procedure(value = "snowplow.create.journey.chains", mode = Mode.WRITE)
    @Description("Creates journey chains")
    public Stream<CreateJourneyChainsResult> createJourneyChains() {

        Transaction tx = db.beginTx();

        ResourceIterator<Node> nodes = tx.findNodes(Label.label("User"));
        List<Node> users = nodes.stream().collect( Collectors.toList() );

        AtomicLongMap<String> counters = AtomicLongMap.create(new HashMap<String, Long>());

        // Iterate through all the User nodes in the graph
        for (Node user : users) {

            // create map of timestamps, and pairs of relationship/end nodes (Page)
            Map<Long, Pair<Relationship, Node>> timestampRelationshipNodeMap = new HashMap<>();
            for (Relationship relationship : user.getRelationships(RelationshipType.withName("VIEWED"))) {

                Pair<Relationship, Node> relationshipNodePair = Pair.with(relationship, relationship.getEndNode());
                timestampRelationshipNodeMap.put( (long) relationship.getProperty("timestamp"), relationshipNodePair);

            }

            Node previousPageViewNode = null;
            Node firstNodeInChain = null;
            for (Map.Entry<Long, Pair<Relationship, Node>> entry : timestampRelationshipNodeMap.entrySet()) {

                Node pageNode = (Node) entry.getValue().getValue(1);
                Node pageViewNode = tx.createNode(Label.label("PageView"));
                pageViewNode.setProperty("page_url", pageNode.getProperty("page_url"));
                pageViewNode.setProperty("timestamp", entry.getKey());

                if (previousPageViewNode != null) {
                    previousPageViewNode.createRelationshipTo(pageViewNode, RelationshipType.withName("NEXT"));

                } else {
                    firstNodeInChain = pageViewNode;
                }

                previousPageViewNode = pageViewNode;

                counters.incrementAndGet("chainsCreated");

            }

            user.createRelationshipTo(firstNodeInChain, RelationshipType.withName("HAS_CHAIN"));

        }

        tx.commit();

        return Stream.of(new CreateJourneyChainsResult(counters));

    }

}
