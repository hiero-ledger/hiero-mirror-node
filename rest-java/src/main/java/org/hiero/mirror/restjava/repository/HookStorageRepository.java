// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageRepository
        extends PagingAndSortingRepository<HookStorage, Id>, HookStorageRepositoryCustom {}
