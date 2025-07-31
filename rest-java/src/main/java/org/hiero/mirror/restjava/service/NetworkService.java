// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;

public interface NetworkService {
    NetworkStake getLatestNetworkStake();
}
