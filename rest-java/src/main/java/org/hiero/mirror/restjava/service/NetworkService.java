// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;

public interface NetworkService {
    NetworkStake getLatestNetworkStake();

    NetworkSupply getSupply(Bound timestamp);

    List<NetworkNodeRow> getNetworkNodes(NetworkNodeRequest request);
}
