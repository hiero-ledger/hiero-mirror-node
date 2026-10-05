// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageChangeRepository
        extends PagingAndSortingRepository<HookStorageChange, Long>, HookStorageChangeRepositoryCustom {}
