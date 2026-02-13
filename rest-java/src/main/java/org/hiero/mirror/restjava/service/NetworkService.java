// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hederahashgraph.api.proto.java.Transaction;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.restjava.dto.NetworkSupply;

public interface NetworkService {
    FeeEstimateResponse estimateFees(Transaction transaction, FeeEstimateMode mode);

    NetworkStake getLatestNetworkStake();

    NetworkSupply getSupply(Bound timestamp);
}
