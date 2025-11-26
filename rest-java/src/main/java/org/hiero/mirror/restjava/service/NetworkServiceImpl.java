// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.repository.NetworkStakeRepository;
import org.hiero.mirror.restjava.repository.NetworkSupplyRepositoryCustom;

@Named
@RequiredArgsConstructor
final class NetworkServiceImpl implements NetworkService {

    private final NetworkStakeRepository networkStakeRepository;
    private final NetworkSupplyRepositoryCustom networkSupplyRepository;

    @Override
    public NetworkStake getLatestNetworkStake() {
        return networkStakeRepository
                .findLatest()
                .orElseThrow(() -> new EntityNotFoundException("No network stake data found"));
    }

    @Override
    public NetworkSupply getSupply(Bound timestamp) {
        return networkSupplyRepository.getSupply(timestamp);
    }
}
