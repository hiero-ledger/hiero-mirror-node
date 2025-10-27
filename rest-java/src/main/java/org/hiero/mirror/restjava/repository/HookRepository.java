// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.domain.hook.Hook;
import org.springframework.data.repository.CrudRepository;

public interface HookRepository extends CrudRepository<Hook, AbstractHook.Id>, HookRepositoryCustom {}
