// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;

public interface HookService {

    Collection<Hook> getHooks(HooksRequest hooksRequest);

    Collection<HookStorage> getHookStorage(HookStorageRequest request);

    Collection<HookStorage> getHookStorageChange(HookStorageRequest request);
}
