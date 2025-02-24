// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation;

import com.hedera.mirror.web3.evm.store.Store;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 */
public interface QueryResourceUsageEstimator {
    /**
     * Flags whether the estimator applies to the given query.
     *
     * @param query the query in question
     * @return if the estimator applies
     */
    boolean applicableTo(final Query query);

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world and response type.
     *
     * @param query the query in question
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGivenType(final Query query) {
        return usageGiven(query);
    }

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world.
     *
     * @param query the query in question
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGiven(final Query query) {
        return usageGiven(query, null);
    }

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world, with a context for storing any information that may be useful for by later stages of
     * the query answer flow.
     *
     * @param query    the query in question
     * @param queryCtx the context of the query being answered
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    FeeData usageGiven(final Query query, @Nullable final Map<String, Object> queryCtx);
}
