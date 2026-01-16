// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;

@Named
@RequiredArgsConstructor
final class CustomFeeServiceImpl implements CustomFeeService {

    private final CustomFeeRepository customFeeRepository;

    @Override
    public CustomFee findById(EntityId id) {
        return customFeeRepository
                .findById(id.getId())
                .orElseThrow(() -> new EntityNotFoundException("Custom fee for entity not found"));
    }
}
