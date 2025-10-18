// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.Nullable;

/**
 * An interface which serves as a facade over the mirror-node specific in-memory state. This interface is used by components
 * inside com.hedera.services package, would be deleted and having this facade would make this task easier.
 * <p>
 * Common methods that are used for interaction with the state are defined here.
 */
public interface Store {

    StackedStateFrames getStackedStateFrames();

    Account getAccount(Address address, OnMissing throwIfMissing);

    /**
     * Load fungible or non-fungible token from the in-memory state.
     */
    Token getToken(Address address, OnMissing throwIfMissing);

    /**
     *
     * This is only to be used when pausing/unpausing token as this method ignores the pause status
     * of the token.
     *
     */
    Token loadPossiblyPausedToken(final Address tokenAddress);

    TokenRelationship getTokenRelationship(TokenRelationshipKey tokenRelationshipKey, OnMissing throwIfMissing);

    /**
     * Load non-fungible token from the in-memory state specified by its serial number.
     */
    UniqueToken getUniqueToken(NftId nftId, OnMissing throwIfMissing);

    void updateAccount(Account updatedAccount);

    void linkAlias(final Address alias, final Address address);

    void deleteAccount(Address accountAddress);

    void updateTokenRelationship(TokenRelationship updatedTokenRelationship);

    void deleteTokenRelationship(TokenRelationship tokenRelationship);

    /**
     * Update fungible or non-fungible token into the in-memory state.
     */
    void updateToken(Token fungibleToken);

    void updateUniqueToken(UniqueToken updatedUniqueToken);

    boolean hasAssociation(TokenRelationshipKey tokenRelationshipKey);

    /**
     * Updating the in-memory state with current pending changes that are part of the current transaction.
     */
    void commit();

    /**
     * Adding a safe layer on top of the in-memory state to write to, while still using the database as a backup.
     */
    void wrap();

    boolean hasApprovedForAll(Address ownerAddress, AccountID operatorId, TokenID tokenId);

    Token loadUniqueTokens(Token token, List<Long> serialNumbers);

    boolean exists(Address accountID);

    Optional<Long> getHistoricalTimestamp();

    Account loadAccountOrFailWith(final Address evmAddress, @Nullable final ResponseCodeEnum responseCodeEnum);

    enum OnMissing {
        THROW,
        DONT_THROW
    }
}
