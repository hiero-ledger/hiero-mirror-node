// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.springframework.data.domain.Persistable;

/**
 * A single entry in a Channel's append-only message queue (Data, Response, or Control). Each
 * {@code (channelId, messageId)} is written once and never mutated, so this is a plain insert-only table, not a
 * current/history pair, the same shape as {@link org.hiero.mirror.common.domain.hook.HookStorageChange}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@jakarta.persistence.Entity
@IdClass(ClprMessage.Id.class)
@NoArgsConstructor
public class ClprMessage implements Persistable<ClprMessage.Id> {

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] channelId;

    @ToString.Exclude
    private byte[] connectorId;

    private long consensusTimestamp;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private ClprControlMessage controlMessage;

    @ToString.Exclude
    private byte[] messageData;

    @jakarta.persistence.Id
    private long messageId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprMessageType messageType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprMessageReplyStatus replyStatus;

    private Long replyToMessageId;

    @ToString.Exclude
    private byte[] runningHash;

    @ToString.Exclude
    private byte[] sender;

    @ToString.Exclude
    private byte[] targetApplication;

    @Override
    @JsonIgnore
    public Id getId() {
        var id = new Id();
        id.setChannelId(channelId);
        id.setMessageId(messageId);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -3157942860172649351L;

        private byte[] channelId;
        private long messageId;
    }
}
