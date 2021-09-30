package io.woolford.snowplow.neo4j;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.*;
import org.neo4j.harness.junit.rule.Neo4jRule;

import java.util.concurrent.TimeUnit;


public class CreateJourneyChainsTest {

    @Rule
    public final Neo4jRule neo4j = new Neo4jRule().withProcedure(CreateJourneyChains.class)
            .withFixture(MODEL_STATEMENT);

    private static final String MODEL_STATEMENT =
            "CREATE (u:User {domain_userid: 'x'})\n" +
                    "CREATE (p1:Page {page_title: 'page1', page_url: 'https://woolford.io/page1.woolford.io/'})\n" +
                    "CREATE (u)-[:VIEWED {timestamp: 1628242395000}]->(p1)\n" +
                    "CREATE (p2:Page {page_title: 'page2', page_url: 'https://woolford.io/page2.woolford.io/'})\n" +
                    "CREATE (u)-[:VIEWED {timestamp: 1628242395001}]->(p2) ";

    @Test
    public void appendJourneyChain() {

        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), configuration())) {

            Session session = driver.session();

            Result result = session.run("CALL snowplow.create.journey.chains()");

        }

    }

    private Config configuration() {
        return Config.builder().withoutEncryption()
                .withLogging(Logging.none())
                .withConnectionTimeout(10, TimeUnit.SECONDS)
                .build();
    }

}
