// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove unused methods: activeRates, updateRates, fcActiveRates
 */
public interface HbarCentExchange {

    ExchangeRate activeRate(Instant now);

    ExchangeRate rate(Timestamp at);
}
