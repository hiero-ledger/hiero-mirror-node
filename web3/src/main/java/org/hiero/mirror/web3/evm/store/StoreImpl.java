// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.web3.evm.store.CachingStateFrame.CacheAccessIncorrectTypeException;
import org.hiero.mirror.web3.evm.store.UpdatableReferenceCache.UpdatableCacheUsageException;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.Nullable;

@Named
public class StoreImpl implements Store {

    private final StackedStateFrames stackedStateFrames;

    private final OptionValidator validator;

    public StoreImpl(final StackedStateFrames stackedStateFrames, final OptionValidator validator) {
        this.stackedStateFrames = stackedStateFrames;
        this.validator = validator;
    }

    @Override
    public StackedStateFrames getStackedStateFrames() {
        return stackedStateFrames;
    }

    @Override
    public Account getAccount(final Address address, final OnMissing throwIfMissing) {
        try {
            final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
            final var account = accountAccessor.get(address);

            if (OnMissing.THROW.equals(throwIfMissing)) {
                return account.orElseThrow(() -> missingEntityException(Account.class, address));
            } else {
                return account.orElse(Account.getEmptyAccount());
            }
        } catch (CacheAccessIncorrectTypeException e) {
            return Account.getEmptyAccount();
        }
    }

    @Override
    public Token getToken(final Address address, final OnMissing throwIfMissing) {
        try {
            final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
            final var token = tokenAccessor.get(address);

            if (OnMissing.THROW.equals(throwIfMissing)) {
                return token.orElseThrow(() -> missingEntityException(Token.class, address));
            } else {
                return token.orElse(Token.getEmptyToken());
            }
        } catch (CacheAccessIncorrectTypeException e) {
            return Token.getEmptyToken();
        }
    }

    @Override
    public Token loadPossiblyPausedToken(final Address tokenAddress) {
        final var token = getToken(tokenAddress, OnMissing.DONT_THROW);

        validateTrue(!token.isEmptyToken(), INVALID_TOKEN_ID);
        validateFalse(token.isDeleted(), TOKEN_WAS_DELETED);

        return token;
    }

    @Override
    public TokenRelationship getTokenRelationship(
            final TokenRelationshipKey tokenRelationshipKey, final OnMissing throwIfMissing) {
        try {
            final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
            final var tokenRelationship = tokenRelationshipAccessor.get(tokenRelationshipKey);

            if (OnMissing.THROW.equals(throwIfMissing)) {
                return tokenRelationship.orElseThrow(
                        () -> missingEntityException(TokenRelationship.class, tokenRelationshipKey));
            } else {
                return tokenRelationship.orElse(TokenRelationship.getEmptyTokenRelationship());
            }
        } catch (CacheAccessIncorrectTypeException e) {
            return TokenRelationship.getEmptyTokenRelationship();
        }
    }

    @Override
    public UniqueToken getUniqueToken(final NftId nftId, final OnMissing throwIfMissing) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        final var uniqueToken = uniqueTokenAccessor.get(nftId);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return uniqueToken.orElseThrow(() -> missingEntityException(UniqueToken.class, nftId));
        } else {
            return uniqueToken.orElse(UniqueToken.getEmptyUniqueToken());
        }
    }

    @Override
    public void updateAccount(final Account updatedAccount) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        accountAccessor.set(updatedAccount.getAccountAddress(), updatedAccount);

        final var canonicalAddress = updatedAccount.canonicalAddress();
        if (canonicalAddress != null && !canonicalAddress.equals(updatedAccount.getAccountAddress())) {
            accountAccessor.set(canonicalAddress, updatedAccount);
        }
    }

    @Override
    public void linkAlias(final Address alias, final Address address) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        final var foundAccount = getAccount(address, OnMissing.DONT_THROW);

        if (foundAccount.isEmptyAccount()) {
            final var accountId = EntityIdUtils.accountIdFromEvmAddress(address);
            final var id = Id.fromGrpcAccount(accountId);
            final var account = new Account(
                    ByteString.copyFrom(alias.toArray()),
                    EntityIdUtils.entityIdFromId(id).getNum(),
                    id,
                    0L);
            accountAccessor.set(alias, account);
            accountAccessor.set(Id.fromGrpcAccount(accountId).asEvmAddress(), account);
        } else {
            accountAccessor.set(alias, foundAccount);
        }
    }

    @Override
    public void deleteAccount(final Address accountAddress) {
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(Account.class);
        try {
            final var account = accountAccessor.get(accountAddress);
            if (account.isEmpty()) {
                return;
            }

            accountAccessor.delete(accountAddress);

            final var canonicalAddress = account.get().canonicalAddress();
            if (canonicalAddress != accountAddress) {
                accountAccessor.delete(canonicalAddress);
            }
        } catch (UpdatableCacheUsageException ex) {
            // ignore, value has been deleted
        }
    }

    @Override
    public void updateTokenRelationship(final TokenRelationship updatedTokenRelationship) {
        final var persistedTokenRel = updatedTokenRelationship.setNotYetPersisted(false);
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
        final var tokenRelationshipKey = keyFromRelationship(persistedTokenRel);
        tokenRelationshipAccessor.set(tokenRelationshipKey, persistedTokenRel);

        final var tokenRelationshipKeyWithAlias = keyFromRelationshipWithAlias(persistedTokenRel);
        if (!tokenRelationshipKeyWithAlias.equals(tokenRelationshipKey)) {
            tokenRelationshipAccessor.set(tokenRelationshipKeyWithAlias, persistedTokenRel);
        }
    }

    @Override
    public void deleteTokenRelationship(TokenRelationship tokenRelationship) {
        final var topFrame = stackedStateFrames.top();
        final var tokenRelationshipAccessor = topFrame.getAccessor(TokenRelationship.class);
        final var tokenRelationshipKey = keyFromRelationship(tokenRelationship);
        try {
            final var tokenRel = tokenRelationshipAccessor.get(tokenRelationshipKey);
            if (tokenRel.isPresent()) {
                tokenRelationshipAccessor.delete(tokenRelationshipKey);
            }
        } catch (UpdatableCacheUsageException ex) {
            // ignore, value has been deleted
        }
        final var tokenRelationshipKeyAlias = keyFromRelationshipWithAlias(tokenRelationship);

        if (tokenRelationshipKeyAlias.equals(tokenRelationshipKey)) {
            return;
        }

        try {
            final var tokenRelAlias = tokenRelationshipAccessor.get(tokenRelationshipKeyAlias);
            if (tokenRelAlias.isPresent()) {
                tokenRelationshipAccessor.delete(tokenRelationshipKeyAlias);
            }
        } catch (UpdatableCacheUsageException ex) {
            // ignore, value has been deleted
        }
    }

    @Override
    public void updateToken(final Token fungibleToken) {
        final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        tokenAccessor.set(fungibleToken.getId().asEvmAddress(), fungibleToken);
    }

    @Override
    public void updateUniqueToken(final UniqueToken updatedUniqueToken) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        uniqueTokenAccessor.set(updatedUniqueToken.getNftId(), updatedUniqueToken);
    }

    @Override
    public boolean hasAssociation(TokenRelationshipKey tokenRelationshipKey) {
        TokenRelationship tokenRelationship = getTokenRelationship(tokenRelationshipKey, OnMissing.DONT_THROW);
        return !(tokenRelationship.getAccount().getId().equals(Id.DEFAULT));
    }

    @Override
    public boolean hasApprovedForAll(Address ownerAddress, AccountID operatorId, TokenID tokenId) {
        if (Address.ZERO.equals(ownerAddress)) {
            return false;
        }
        final Set<FcTokenAllowanceId> approvedForAll =
                getAccount(ownerAddress, OnMissing.THROW).getApproveForAllNfts();
        return approvedForAll.contains(FcTokenAllowanceId.from(tokenId, operatorId));
    }

    @Override
    public void commit() {
        if (stackedStateFrames.height() > 1) { // commit only to upstream RWCachingStateFrame
            stackedStateFrames.top().commit();
            stackedStateFrames.pop();
        }
    }

    @Override
    public void wrap() {
        stackedStateFrames.push();
    }

    @Override
    public boolean exists(final Address address) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        final var account = accountAccessor.get(address);
        return account.isPresent();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Long> getHistoricalTimestamp() {
        return stackedStateFrames
                .top()
                .upstreamFrame
                // flatten the nested Optional<UpstreamFrame>
                .flatMap(CachingStateFrame::getUpstream)
                // filter out non-DatabaseBackedStateFrame instances
                .filter(DatabaseBackedStateFrame.class::isInstance)
                // cast the filtered object to DatabaseBackedStateFrame
                .map(DatabaseBackedStateFrame.class::cast)
                // return the timestamp
                .flatMap(databaseBackedStateFrame -> databaseBackedStateFrame.timestamp);
    }

    /**
     * Returns a {@link Account} model with loaded account from the state and throws the given code if an exception
     * occurs due to an invalid account.
     *
     * @param evmAddress of the account to load
     * @param code       the {@link ResponseCodeEnum} to fail with if the account is deleted/missing
     * @return a usable model of the account if available
     */
    @Override
    public Account loadAccountOrFailWith(final Address evmAddress, @Nullable ResponseCodeEnum code) {
        final var account = getAccount(evmAddress, OnMissing.DONT_THROW);
        validateUsable(account, code, INVALID_ACCOUNT_ID, ACCOUNT_DELETED);
        return account;
    }

    private void validateUsable(
            final Account account,
            @Nullable final ResponseCodeEnum explicitResponse,
            final ResponseCodeEnum nonExistingCode,
            final ResponseCodeEnum deletedCode) {
        validateTrue(account != null, explicitResponse != null ? explicitResponse : nonExistingCode);
        validateFalse(account.isDeleted(), explicitResponse != null ? explicitResponse : deletedCode);
        final var expiryStatus =
                validator.expiryStatusGiven(this, accountIdFromEvmAddress(account.getAccountAddress()));
        validateTrue(expiryStatus == OK, expiryStatus);
    }

    /**
     * Returns a {@link Token} model with loaded unique tokens
     *
     * @param token         the token model, on which to load the unique tokens
     * @param serialNumbers the serial numbers to load
     * @throws com.hedera.node.app.service.evm.exceptions.InvalidTransactionException if the requested token class is
     *                                                                                missing, deleted, or expired and
     *                                                                                pending removal
     */
    public Token loadUniqueTokens(final Token token, final List<Long> serialNumbers) {
        final var loadedUniqueTokens = new HashMap<Long, UniqueToken>();
        for (final long serialNumber : serialNumbers) {
            final var uniqueToken = loadUniqueToken(token.getId(), serialNumber);
            loadedUniqueTokens.put(serialNumber, uniqueToken);
        }

        return token.setLoadedUniqueTokens(loadedUniqueTokens);
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can be used to implement
     * business logic in a transaction.
     *
     * @param tokenId   TokenId of the NFT
     * @param serialNum Serial number of the NFT
     * @return The {@link UniqueToken} model of the requested unique token
     */
    private UniqueToken loadUniqueToken(final Id tokenId, final Long serialNum) {
        final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNum);
        return getUniqueToken(nftId, OnMissing.THROW);
    }

    private TokenRelationshipKey keyFromRelationship(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().getAccountAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }

    private TokenRelationshipKey keyFromRelationshipWithAlias(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().canonicalAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }

    private InvalidTransactionException missingEntityException(final Class<?> type, Object id) {
        return new InvalidTransactionException(
                String.format("Entity of type %s with id %s is missing", type.getName(), id), FAIL_INVALID, true);
    }
}
