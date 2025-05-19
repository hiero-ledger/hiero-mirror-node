// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import java.security.PublicKey;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.addressbook.ConsensusNode;

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
