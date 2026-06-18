// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

final class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
