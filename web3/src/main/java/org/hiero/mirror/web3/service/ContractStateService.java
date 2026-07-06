// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;

public interface ContractStateService {

    Optional<byte[]> findStorage(EntityId contractId, byte[] key);

    Optional<byte[]> findStorageByBlockTimestamp(EntityId entityId, byte[] slotKeyByteArray, long blockTimestamp);
}
