// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository.upsert;

public interface UpsertQueryGenerator {

    String TEMP_SUFFIX = "_temp";

    String getFinalTableName();

    default String getTemporaryTableName() {
        return getFinalTableName() + TEMP_SUFFIX;
    }

    String getUpsertQuery();
}
