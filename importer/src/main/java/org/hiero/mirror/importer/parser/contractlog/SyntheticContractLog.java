// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public interface SyntheticContractLog {
    RecordItem getRecordItem();

    EntityId getEntityId();

    byte[] getTopic0();

    byte[] getTopic1();

    byte[] getTopic2();

    byte[] getTopic3();

    byte[] getData();
}
