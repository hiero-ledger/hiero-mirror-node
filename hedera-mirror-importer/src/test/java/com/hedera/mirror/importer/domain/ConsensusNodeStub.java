// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.domain;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import java.security.PublicKey;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder
@Data
@EqualsAndHashCode(of = "nodeId")
@ToString(exclude = "publicKey")
public class ConsensusNodeStub implements ConsensusNode {
    private EntityId nodeAccountId;
    private long nodeId;
    private PublicKey publicKey;
    private long stake;
    private long totalStake;
}
