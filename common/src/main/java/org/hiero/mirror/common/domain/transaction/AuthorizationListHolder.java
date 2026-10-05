// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code ethereum_transaction.authorization_list} (JSONB). A bare {@code List} is
 * treated by Spring Data JDBC as a separate aggregate table.
 */
public record AuthorizationListHolder(List<Authorization> items) implements Serializable {

    public static AuthorizationListHolder of(List<Authorization> list) {
        if (list == null) {
            return null;
        }
        return new AuthorizationListHolder(List.copyOf(list));
    }
}
