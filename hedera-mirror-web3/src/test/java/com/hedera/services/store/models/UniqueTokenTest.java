// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.services.utils.IdUtils.asModelId;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.state.submerkle.RichInstant;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UniqueTokenTest {
    @Test
    void objectContractWorks() {
        final var id = new Id(1, 2, 3);
        final var metadata = new byte[] {111, 23, 85};
        final var tokenId = Id.DEFAULT;
        final var subj = new UniqueToken(tokenId, 1, RichInstant.MISSING_INSTANT, id, null, metadata);
        assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
        assertEquals(id, subj.getOwner());
        assertEquals(1, subj.getSerialNumber());
        assertEquals(Id.DEFAULT, subj.getTokenId());
        assertEquals(Id.DEFAULT.asEvmAddress(), subj.getAddress());
        assertEquals(metadata, subj.getMetadata());
        assertEquals(null, subj.getSpender());
        assertEquals(new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), 1), subj.getNftId());
    }

    @Test
    void toStringWorks() {
        final var token1 = asModelId("0.0.12345");
        final var owner1 = asModelId("0.0.12346");
        final var spender1 = asModelId("0.0.12347");
        final var meta1 = "aa".getBytes(StandardCharsets.UTF_8);
        final var subject = new UniqueToken(token1, 1L, RichInstant.MISSING_INSTANT, owner1, spender1, meta1);

        final var expected = "UniqueToken{tokenID=0.0.12345, serialNum=1, metadata=[97, 97],"
                + " creationTime=RichInstant{seconds=0, nanos=0}, owner=0.0.12346,"
                + " spender=0.0.12347}";

        assertEquals(expected, subject.toString());
    }
}
