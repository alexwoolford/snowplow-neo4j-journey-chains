# snowplow-neo4j-journey-chains

This repo contains two Neo4j stored procedures to create a chain of pageviews connected by the `:NEXT` relationship, for each `domain_userid`:

* snowplow.append.journey.chain(domain_userid, page_url, timestamp)
* snowplow.create.journey.chains()

The `snowplow.append.journey.chain` procedure appends new pageviews to the end of the end of the chains. The properties are passed as arguments. This is suitable for real-time ingest since it processes a single pageview at a time. We use this in a Kafka sink for continual ingest.

The `snowplow.create.journey.chains` procedure creates chains for a `(:User)-[:VIEWED]-(:Page)` graph.

For performance, we indexed the `page_url` property in the `PageView` nodes:

    CREATE INDEX pageview_page_url_idx IF NOT EXISTS
    FOR (n:PageView)
    ON (n.page_url)

We can then see which pages are viewed _after_ viewing any particular page:

    neoadmin@neo4j> :use snowplow

    neoadmin@snowplow> :param page_url => 'https://woolford.io/2021-07-19-capture-snowplow-events-in-ccloud/'

    neoadmin@snowplow> MATCH (pv:PageView)
    WHERE pv.page_url = $page_url
    MATCH (pv)-[:NEXT]->(pvNext)
    WHERE pvNext.page_url <> $page_url
    WITH pvNext
    RETURN pvNext.page_url, COUNT(*) AS pageViews
    ORDER BY pageViews DESC
    LIMIT 3;

    +-----------------------------------------------------------------------------+
    | pvNext.page_url                                                 | pageViews |
    +-----------------------------------------------------------------------------+
    | "https://woolford.io/2021-08-09-snowplow-neo4j-recommender/"    | 41        |
    | "https://woolford.io/"                                          | 30        |
    | "https://woolford.io/2021-06-02-monitor-neo4j-with-prometheus/" | 5         |
    +-----------------------------------------------------------------------------+



![pageview-chain](img/pageview_chain.png)




    MATCH(p:PageView) DETACH DELETE p;

    CALL snowplow.create.journey.chains();

    MATCH (u:User {domain_userid: '47aeb6c3-ce09-48d6-a66d-d4223669dc39'})
    MATCH (u)-[:HAS_CHAIN]->(pv1)
    MATCH path = (pv1)-[:NEXT*0..]->()
    RETURN path;


Helpful Kafka functions:

    http DELETE snowplow.woolford.io:8083/connectors/snowplow-local-neo4j

    kafka-consumer-groups.sh --bootstrap-server pkc-lzvrd.us-west4.gcp.confluent.cloud:9092 --command-config config.properties --group connect-snowplow-local-neo4j --topic snowplow-enriched-good-json --reset-offsets --to-earliest --execute

    http PUT snowplow.woolford.io:8083/connectors/snowplow-local-neo4j/config <<< '
    {
        "connector.class": "streams.kafka.connect.sink.Neo4jSinkConnector",
        "key.converter": "org.apache.kafka.connect.storage.StringConverter",
        "name": "snowplow-local-neo4j",
        "neo4j.authentication.basic.password": "********",
        "neo4j.authentication.basic.username": "neoadmin",
        "neo4j.database": "snowplow",
        "neo4j.server.uri": "neo4j://neo4j.woolford.io:7687",
        "neo4j.topic.cypher.snowplow-enriched-good-json": "CALL snowplow.append.journey.chain(event.domain_userid, event.page_url, apoc.date.fromISO8601(event.derived_tstamp)) YIELD chainLength RETURN chainLength",
        "topics": "snowplow-enriched-good-json",
        "value.converter": "org.apache.kafka.connect.json.JsonConverter",
        "value.converter.schemas.enable": "false"
    }'


[//]: # (TODO: fix chainLength metric)

