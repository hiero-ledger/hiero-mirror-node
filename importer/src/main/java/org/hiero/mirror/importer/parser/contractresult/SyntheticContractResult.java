// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractresult;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public interface SyntheticContractResult {
    RecordItem getRecordItem();

    EntityId getEntityId();

    EntityId getSenderId();

    byte[] getFunctionParameters();
}
