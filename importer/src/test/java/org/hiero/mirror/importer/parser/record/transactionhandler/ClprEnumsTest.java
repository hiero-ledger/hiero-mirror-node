// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.junit.jupiter.api.Test;

class ClprEnumsTest {

    @Test
    void toReplyStatus() {
        assertThat(ClprEnums.toReplyStatus(com.hederahashgraph.api.proto.java.ClprMessageReplyStatus.SUCCESS))
                .isEqualTo(ClprMessageReplyStatus.SUCCESS);
    }

    @Test
    void toReplyStatusUnrecognized() {
        assertThat(ClprEnums.toReplyStatus(com.hederahashgraph.api.proto.java.ClprMessageReplyStatus.UNRECOGNIZED))
                .isNull();
    }

    @Test
    void toSignatureScheme() {
        assertThat(ClprEnums.toSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme.ED25519))
                .isEqualTo(ClprSignatureScheme.ED25519);
    }

    @Test
    void toSignatureSchemeUnrecognized() {
        assertThat(ClprEnums.toSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme.UNRECOGNIZED))
                .isNull();
    }
}
