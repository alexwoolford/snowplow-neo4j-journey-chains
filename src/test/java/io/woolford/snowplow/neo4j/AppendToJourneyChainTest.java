package io.woolford.snowplow.neo4j;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.*;
import org.neo4j.harness.junit.rule.Neo4jRule;

import java.util.concurrent.TimeUnit;

public class AppendToJourneyChainTest {

    @Rule
    public final Neo4jRule neo4j = new Neo4jRule().withProcedure(AppendToJourneyChain.class)
            .withFixture(MODEL_STATEMENT);

    private static final String MODEL_STATEMENT =
            "CREATE (u:User {domain_userid: 'x'})\n" +
                    "CREATE (p1:Page {page_title: 'page1', page_url: 'https://woolford.io/page1.woolford.io/'})\n" +
                    "CREATE (u)-[:VIEWED {timestamp: 1628242395000}]->(p1)\n" +
                    "CREATE (p2:Page {page_title: 'page2', page_url: 'https://woolford.io/page2.woolford.io/'})\n" +
                    "CREATE (u)-[:VIEWED {timestamp: 1628242395001}]->(p2) ";

    @Test
    public void appendToJourneyChainExistingDomainUserid() {

        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), configuration())) {

            Session session = driver.session();

            Result result1 = session.run("CALL snowplow.append.journey.chain('x', 'page1', 1632860772150)");
            Result result2 = session.run("CALL snowplow.append.journey.chain('x', 'page2', 1632860772151)");
            Result result3 = session.run("CALL snowplow.append.journey.chain('x', 'page3', 1632860772152)");

        }

    }

    @Test
    public void appendToJourneyChainNoExistingDomainUserid() {

        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), configuration())) {

            Session session = driver.session();

            Result result = session.run("CALL snowplow.append.journey.chain('y', 'page3', 1632860772157)");

        }

    }

    private Config configuration() {
        return Config.builder().withoutEncryption()
                .withLogging(Logging.none())
                .withConnectionTimeout(10, TimeUnit.SECONDS)
                .build();
    }

}
