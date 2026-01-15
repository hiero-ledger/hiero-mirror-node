// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import org.hiero.mirror.common.domain.entity.EntityId;

public interface ContractBytecodeService {

    byte[] get(EntityId fileId);
}
