// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.restjava.repository.NetworkStakeRepository;

@Named
@RequiredArgsConstructor
class NetworkServiceImpl implements NetworkService {

    private final NetworkStakeRepository networkStakeRepository;

    @Override
    public NetworkStake getLatestNetworkStake() {
        return networkStakeRepository
                .findLatest()
                .orElseThrow(() -> new EntityNotFoundException("No network stake data found"));
    }
}
