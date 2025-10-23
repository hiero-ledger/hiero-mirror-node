// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
// import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.common.domain.hook.Hook;

public interface HookService {

    long getActiveHookCount(long ownerId);

    List<Hook> getAllHooksByOwner(long ownerId, String hookIdFilter, int limit, String order);
}
