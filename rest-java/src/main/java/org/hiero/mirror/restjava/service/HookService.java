// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.hook.Hook;

public interface HookService {

    List<Hook> getHooks(long ownerId, String hookIdFilter, int limit, String order);
}
