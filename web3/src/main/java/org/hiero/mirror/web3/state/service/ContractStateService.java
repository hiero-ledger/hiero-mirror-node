// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;

public interface ContractStateService {

    Optional<byte[]> findStorage(EntityId contractId, byte[] key);

    Optional<byte[]> findStorageByBlockTimestamp(Long entityId, byte[] slotKeyByteArray, long blockTimestamp);
}
