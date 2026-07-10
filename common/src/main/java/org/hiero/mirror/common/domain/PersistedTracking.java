// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Persistable;

/**
 * Persistable implementation for aggregates with manually assigned ids that are both inserted and updated via
 * repository save(). Spring Data JDBC can't infer new vs existing from an assigned id, so implementations carry a
 * transient persisted flag that entity callbacks registered in CommonConfiguration set after a load or save.
 */
public interface PersistedTracking<ID> extends Persistable<ID> {

    boolean isPersisted();

    void setPersisted(boolean persisted);

    @JsonIgnore
    @Override
    default boolean isNew() {
        return !isPersisted();
    }
}
