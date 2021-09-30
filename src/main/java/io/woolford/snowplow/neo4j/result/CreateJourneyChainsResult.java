package io.woolford.snowplow.neo4j.result;

import com.google.common.util.concurrent.AtomicLongMap;

public class CreateJourneyChainsResult {

    public final Long chainsCreated;

    public CreateJourneyChainsResult(AtomicLongMap<String> counters) {

        this.chainsCreated = counters.get("chainsCreated");

    }

}
