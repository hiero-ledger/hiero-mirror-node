// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageResult;
import org.hiero.mirror.restjava.dto.HooksRequest;

public interface HookService {

    Collection<Hook> getHooks(HooksRequest hooksRequest);

    HookStorageResult getHookStorage(HookStorageRequest request);
}
