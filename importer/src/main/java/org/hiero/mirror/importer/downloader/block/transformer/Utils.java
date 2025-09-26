// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.trace.protoc.EvmTransactionLog;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.ArrayList;
import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.util.DomainUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

@UtilityClass
final class Utils {

    private static final int TOPIC_SIZE_BYTES = 32;

    static Address asAddress(ContractID contractId) {
        return Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(contractId)));
    }

    static LogsBloomFilter bloomFor(EvmTransactionLog log) {
        var logger = asAddress(log.getContractId());
        var topics = new ArrayList<LogTopic>();
        for (var topic : log.getTopicsList()) {
            topics.add(LogTopic.wrap(Bytes.wrap(leftPad32(topic))));
        }
        var besuLog = new Log(logger, Bytes.wrap(DomainUtils.toBytes(log.getData())), topics);
        return LogsBloomFilter.builder().insertLog(besuLog).build();
    }

    static LogsBloomFilter bloomForAll(Collection<LogsBloomFilter> bloomFilters) {
        var builder = LogsBloomFilter.builder();
        for (var bloomFilter : bloomFilters) {
            builder.insertFilter(bloomFilter);
        }
        return builder.build();
    }

    static byte[] leftPad32(ByteString topic) {
        byte[] bytes = DomainUtils.toBytes(topic);
        int length = bytes.length;
        if (length < TOPIC_SIZE_BYTES) {
            byte[] padded = new byte[TOPIC_SIZE_BYTES];
            System.arraycopy(bytes, 0, padded, TOPIC_SIZE_BYTES - length, length);
            return padded;
        }

        return bytes;
    }
}
