// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.hedera.node.app.state.recordcache.schemas.V0540RecordCacheSchema;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum StateKeyRegistry {
    ;
    private static final Set<String> keys = Stream.of(
                    // Implemented
                    AccountReadableKVState.KEY,
                    AirdropsReadableKVState.KEY,
                    AliasesReadableKVState.KEY,
                    ContractBytecodeReadableKVState.KEY,
                    ContractStorageReadableKVState.KEY,
                    FileReadableKVState.KEY,
                    NftReadableKVState.KEY,
                    TokenReadableKVState.KEY,
                    TokenRelationshipReadableKVState.KEY,
                    // Not implemented but not needed
                    V0490TokenSchema.STAKING_INFO_KEY,
                    V0490FileSchema.UPGRADE_DATA_KEY,
                    V0490RecordCacheSchema.TXN_RECORD_QUEUE,
                    V0540RecordCacheSchema.TXN_RECEIPT_QUEUE,
                    V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY,
                    V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY,
                    V0570ScheduleSchema.SCHEDULED_COUNTS_KEY,
                    V0570ScheduleSchema.SCHEDULED_ORDERS_KEY,
                    V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY,
                    V0570ScheduleSchema.SCHEDULED_USAGES_KEY,
                    // Not implemented but needed
                    V0490ScheduleSchema.SCHEDULES_BY_ID_KEY)
            .collect(Collectors.toSet());

    public static boolean contains(final String stateKey) {
        if (stateKey != null && stateKey.startsWith("UPGRADE_DATA")) {
            return keys.contains(V0490FileSchema.UPGRADE_DATA_KEY);
        }
        return keys.contains(stateKey);
    }
}
