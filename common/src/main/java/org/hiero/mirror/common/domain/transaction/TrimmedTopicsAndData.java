// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.hiero.mirror.common.util.DomainUtils;

@Value
@RequiredArgsConstructor
public class TrimmedTopicsAndData {

    ByteString topic1;
    ByteString topic2;
    ByteString topic3;
    ByteString topic4;
    ByteString data;

    public TrimmedTopicsAndData(ContractLoginfo contractLoginfo) {
        var topicCount = contractLoginfo.getTopicList().size();
        topic1 = topicCount > 0 ? DomainUtils.trim(contractLoginfo.getTopic(0)) : ByteString.EMPTY;
        topic2 = topicCount > 1 ? DomainUtils.trim(contractLoginfo.getTopic(1)) : ByteString.EMPTY;
        topic3 = topicCount > 2 ? DomainUtils.trim(contractLoginfo.getTopic(2)) : ByteString.EMPTY;
        topic4 = topicCount > 3 ? DomainUtils.trim(contractLoginfo.getTopic(3)) : ByteString.EMPTY;
        data = DomainUtils.trim(contractLoginfo.getData());
    }
}
