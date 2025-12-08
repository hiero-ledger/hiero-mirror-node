// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import static org.hiero.mirror.restjava.common.Constants.RECEIVER_ID;
import static org.hiero.mirror.restjava.common.Constants.SENDER_ID;
import static org.hiero.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.jooq.Field;
import org.springframework.data.domain.Sort;

@Data
@Builder
public class TokenAirdropRequest {

    // Sender Id for Outstanding Airdrops, Receiver Id for Pending Airdrops
    private EntityIdParameter accountId;

    @Builder.Default
    private int limit = 25;

    @Builder.Default
    private Sort.Direction order = Sort.Direction.ASC;

    // Receiver Id for Outstanding Airdrops, Sender Id for Pending Airdrops
    @Builder.Default
    private Bound entityIds = Bound.EMPTY;

    @Builder.Default
    private Bound serialNumbers = Bound.EMPTY;

    @Builder.Default
    private Bound tokenIds = Bound.EMPTY;

    @Builder.Default
    private AirdropRequestType type = AirdropRequestType.OUTSTANDING;

    @Getter
    @RequiredArgsConstructor
    public enum AirdropRequestType {
        OUTSTANDING(TOKEN_AIRDROP.SENDER_ACCOUNT_ID, TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID, RECEIVER_ID),
        PENDING(TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID, TOKEN_AIRDROP.SENDER_ACCOUNT_ID, SENDER_ID);

        // The base field is the conditional clause for the base DB query.
        // The base field is the path parameter accountId, which is Sender Id for Outstanding Airdrops and Receiver Id
        // for Pending Airdrops
        private final Field<Long> baseField;

        // The primary field is the primary sort field for the DB query.
        // The primary field is the optional query parameter 'entityIds', which is Receiver Id for Outstanding Airdrops
        // and Sender Id for Pending Airdrops
        private final Field<Long> primaryField;

        // The primary query parameter
        private final String parameter;
    }

    public List<Bound> getBounds() {
        var primaryBound = !entityIds.isEmpty() ? entityIds : tokenIds;
        if (primaryBound.isEmpty()) {
            return List.of(serialNumbers);
        }

        var secondaryBound = !tokenIds.isEmpty() ? tokenIds : serialNumbers;
        if (secondaryBound.isEmpty()) {
            return List.of(primaryBound);
        }

        return List.of(primaryBound, secondaryBound, serialNumbers);
    }
}
