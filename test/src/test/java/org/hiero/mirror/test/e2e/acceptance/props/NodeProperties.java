// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.props;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.proto.AccountID;
import com.hedera.hashgraph.sdk.proto.NodeAddress;
import com.hedera.hashgraph.sdk.proto.ServiceEndpoint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class NodeProperties {

    @NotBlank
    private String accountId;

    private String certHash;

    @NotBlank
    private String host;

    @Min(0)
    private Long nodeId;

    @Min(0)
    @Max(65535)
    private int port = 50211;

    public String getEndpoint() {
        return host + ":" + port;
    }

    public long getNodeId() {
        if (nodeId == null) {
            var nodeAccountId = AccountId.fromString(accountId);
            return nodeAccountId.num - 3;
        }
        return nodeId;
    }

    @SneakyThrows
    public NodeAddress toNodeAddress() {
        var ipAddressV4 = TestUtil.toIpAddressV4(host);
        var sdkAccountId = AccountId.fromString(accountId);

        return NodeAddress.newBuilder()
                .setNodeAccountId(AccountID.newBuilder()
                        .setShardNum(sdkAccountId.shard)
                        .setRealmNum(sdkAccountId.realm)
                        .setAccountNum(sdkAccountId.num))
                .setNodeCertHash(certHash != null ? ByteString.copyFromUtf8(certHash) : ByteString.EMPTY)
                .setNodeId(getNodeId())
                .addServiceEndpoint(ServiceEndpoint.newBuilder()
                        .setDomainName(ipAddressV4.isEmpty() ? host : "")
                        .setIpAddressV4(ipAddressV4)
                        .setPort(port))
                .build();
    }
}
