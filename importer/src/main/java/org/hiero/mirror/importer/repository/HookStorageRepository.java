// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.hook.HookStorage;
import org.springframework.data.repository.CrudRepository;

public interface HookStorageRepository extends CrudRepository<HookStorage, HookStorage.Id> {}
