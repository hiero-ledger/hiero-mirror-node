// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A single entry in a Channel's append-only message queue (Data, Response, or Control); insert-only, same shape as
 * {@link org.hiero.mirror.common.domain.hook.HookStorageChange}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@Table
public class ClprMessage implements Persistable<ClprMessage.Id> {

    @Transient
    @ToString.Exclude
    private byte[] channelId;

    @ToString.Exclude
    private byte[] connectorId;

    private long consensusTimestamp;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    private ClprControlMessage controlMessage;

    @ToString.Exclude
    private byte[] messageData;

    @Transient
    private long messageId;

    private ClprMessageType messageType;

    private ClprMessageReplyStatus replyStatus;

    private Long replyToMessageId;

    @ToString.Exclude
    private byte[] runningHash;

    @ToString.Exclude
    private byte[] sender;

    @ToString.Exclude
    private byte[] targetApplication;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
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
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.channelId = id.getChannelId();
        this.messageId = id.getMessageId();
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
