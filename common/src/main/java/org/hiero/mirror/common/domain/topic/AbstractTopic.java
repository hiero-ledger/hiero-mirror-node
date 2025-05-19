// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.topic;

import com.google.common.collect.Range;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@Upsertable(history = true)
public abstract class AbstractTopic implements History {

    private byte[] adminKey;

    @Column(updatable = false)
    @ToString.Include
    private Long createdTimestamp;

    @Id
    @ToString.Include
    private Long id;

    private byte[] feeExemptKeyList;

    private byte[] feeScheduleKey;

    private byte[] submitKey;

    @ToString.Include
    private Range<Long> timestampRange;
}
