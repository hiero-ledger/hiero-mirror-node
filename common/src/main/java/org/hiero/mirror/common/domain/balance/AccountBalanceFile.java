// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("account_balance_file")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class AccountBalanceFile implements StreamFile<AccountBalance> {

    @ToString.Exclude
    private byte[] bytes;

    @Id
    private Long consensusTimestamp;

    private Long count;

    @ToString.Exclude
    private String fileHash;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Transient
    private List<AccountBalance> items = List.of();

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private boolean synthetic;

    private int timeOffset;

    @Override
    public StreamFile<AccountBalance> copy() {
        return this.toBuilder().build();
    }

    @Override
    public Long getConsensusStart() {
        return consensusTimestamp;
    }

    @Override
    public void setConsensusStart(Long timestamp) {
        consensusTimestamp = timestamp;
    }

    @Override
    public Long getConsensusEnd() {
        return getConsensusStart();
    }

    @Override
    public StreamType getType() {
        return StreamType.BALANCE;
    }
}
