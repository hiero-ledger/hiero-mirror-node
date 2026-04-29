// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("file_data") // Explicit table name naming is recommended
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "fileData")
public class FileData implements Persistable<Long> {

    @Id
    private Long consensusTimestamp;

    @SuppressWarnings("java:S1700")
    private byte[] fileData;

    // Converter removed. Handled by global EntityIdConverter bean.
    private EntityId entityId;

    private Integer transactionType;

    public boolean transactionTypeIsAppend() {
        return transactionType == TransactionType.FILEAPPEND.getProtoId();
    }

    @JsonIgnore
    public int getDataSize() {
        return fileData == null ? 0 : fileData.length;
    }

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Keeps the optimization to skip the "SELECT" check before insert
        return true;
    }
}
