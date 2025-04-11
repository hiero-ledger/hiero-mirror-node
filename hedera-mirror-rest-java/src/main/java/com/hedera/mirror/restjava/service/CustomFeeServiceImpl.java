// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.restjava.repository.CustomFeeRepository;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class CustomFeeServiceImpl implements CustomFeeService {

    private final CustomFeeRepository customFeeRepository;

    @Override
    public CustomFee findById(@Nonnull EntityId id) {
        return customFeeRepository
                .findById(id.getId())
                .orElseThrow(() -> new EntityNotFoundException("Custom fee for entity id %s not found".formatted(id)));
    }
}
