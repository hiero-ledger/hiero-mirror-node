// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.restjava.dto.NetworkSupply;

public interface NetworkService {
    NetworkStake getLatestNetworkStake();

    NetworkSupply getSupply(Bound timestamp);
}
