// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;

public interface HookService {

    HookStorageResult getHookStorage(HookStorageRequest request);

    List<Hook> getHooks(HooksRequest request);
}
