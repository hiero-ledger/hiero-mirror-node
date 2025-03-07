// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.Upsertable;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNode implements History {

    @ToString.Exclude
    private byte[] adminKey;

    @Column(updatable = false)
    private Long createdTimestamp;

    private boolean deleted;

    @Id
    private Long nodeId;

    private Range<Long> timestampRange;
}
