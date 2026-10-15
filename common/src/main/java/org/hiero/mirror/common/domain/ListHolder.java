// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type wrapping a list persisted as JSONB. A bare {@code List} property is mapped by Spring
 * Data JDBC as a separate aggregate table, so each JSONB list column is declared as a holder implementing this
 * interface and converted by {@link org.hiero.mirror.common.converter.JsonbJdbcConverters}.
 */
public interface ListHolder<T> extends Serializable {

    List<T> items();
}
