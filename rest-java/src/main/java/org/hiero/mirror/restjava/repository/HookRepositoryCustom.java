// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HooksRequest;

public interface HookRepositoryCustom extends JooqRepository {

    Collection<Hook> findAll(HooksRequest request, EntityId accountId);
}
