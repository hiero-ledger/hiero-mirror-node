// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.hook.AbstractHook.Id;
import org.hiero.mirror.common.domain.hook.Hook;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookRepository extends PagingAndSortingRepository<Hook, Id>, HookRepositoryCustom {}
