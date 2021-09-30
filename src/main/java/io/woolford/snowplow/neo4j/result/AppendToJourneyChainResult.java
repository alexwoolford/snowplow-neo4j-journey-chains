package io.woolford.snowplow.neo4j.result;

import com.google.common.util.concurrent.AtomicLongMap;

public class AppendToJourneyChainResult {

    public final Long chainLength;

    public AppendToJourneyChainResult(AtomicLongMap<String> counters) {

        this.chainLength = counters.get("chainLength");

    }

}
