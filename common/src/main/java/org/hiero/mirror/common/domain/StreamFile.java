// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import java.util.List;
import org.jspecify.annotations.NonNull;

public interface StreamFile<T extends StreamItem> {

    default StreamFile<T> clear() {
        setBytes(null);
        setItems(List.of());
        return this;
    }

    StreamFile<T> copy();

    byte[] getBytes();

    void setBytes(byte[] bytes);

    Long getConsensusStart();

    void setConsensusStart(Long timestamp);

    Long getConsensusEnd();

    default void setConsensusEnd(Long timestamp) {}

    Long getCount();

    String getFileHash();

    // Get the chained hash of the stream file
    default String getHash() {
        return null;
    }

    default void setHash(String hash) {}

    default Long getIndex() {
        return null;
    }

    default void setIndex(Long index) {}

    List<T> getItems();

    void setItems(List<T> items);

    Long getLoadEnd();

    Long getLoadStart();

    default String getMetadataHash() {
        return null;
    }

    String getName();

    void setName(String name);

    Long getNodeId();

    void setNodeId(@NonNull Long nodeId);

    // Get the chained hash of the previous stream file
    default String getPreviousHash() {
        return null;
    }

    default void setPreviousHash(String previousHash) {}

    StreamType getType();
}
