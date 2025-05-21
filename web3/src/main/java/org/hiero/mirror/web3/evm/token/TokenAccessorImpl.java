// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.token;

import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.evmKey;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import jakarta.inject.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.exception.ParsingException;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
@Named
public class TokenAccessorImpl implements TokenAccessor {

    private final MirrorNodeEvmProperties properties;
    private final Store store;
    private final MirrorEvmContractAliases aliases;

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(Address address) {
        return getTokenInfo(address);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(nftIdFromEntityId(entityId, serialNo), OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return Optional.empty();
        }

        final var ledgerId = properties.getNetwork().getLedgerId();
        final var owner = nft.getOwner() != null ? nft.getOwner().asEvmAddress() : Address.ZERO;
        final var creationTime = nft.getCreationTime();
        final var metadata = nft.getMetadata();
        final var spender = nft.getSpender() != null ? nft.getSpender().asEvmAddress() : Address.ZERO;
        final var nftInfo = new EvmNftInfo(serialNo, owner, creationTime.getSeconds(), metadata, spender, ledgerId);
        return Optional.of(nftInfo);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        return !store.getToken(address, OnMissing.DONT_THROW).isEmptyToken();
    }

    @Override
    public boolean isFrozen(final Address address, final Address token) {
        final var tokenRelationship =
                store.getTokenRelationship(new TokenRelationshipKey(token, address), OnMissing.DONT_THROW);
        return !tokenRelationship.isEmptyTokenRelationship() && tokenRelationship.isFrozen();
    }

    @Override
    public boolean defaultFreezeStatus(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).isFrozenByDefault();
    }

    @Override
    public boolean defaultKycStatus(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).hasKycKey();
    }

    @Override
    public boolean isKyc(final Address address, final Address token) {
        final var tokenRelationship =
                store.getTokenRelationship(new TokenRelationshipKey(token, address), OnMissing.DONT_THROW);
        return !tokenRelationship.isEmptyTokenRelationship() && tokenRelationship.isKycGranted();
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return Optional.of(getCustomFees(token));
    }

    @Override
    public TokenType typeOf(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).getType();
    }

    @Override
    public EvmKey keyOf(final Address address, final TokenKeyType tokenKeyType) {
        final var tokenInfoOptional = getTokenInfo(address);

        return tokenInfoOptional
                .map(tokenInfo -> switch (tokenKeyType) {
                    case ADMIN_KEY -> tokenInfo.getAdminKey();
                    case KYC_KEY -> tokenInfo.getKycKey();
                    case FREEZE_KEY -> tokenInfo.getFreezeKey();
                    case WIPE_KEY -> tokenInfo.getWipeKey();
                    case SUPPLY_KEY -> tokenInfo.getSupplyKey();
                    case FEE_SCHEDULE_KEY -> tokenInfo.getFeeScheduleKey();
                    case PAUSE_KEY -> tokenInfo.getPauseKey();
                })
                .orElse(new EvmKey());
    }

    @Override
    public String nameOf(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).getName();
    }

    @Override
    public String symbolOf(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).getSymbol();
    }

    @Override
    public long totalSupplyOf(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).getTotalSupply();
    }

    @Override
    public int decimalsOf(final Address address) {
        return store.getToken(address, OnMissing.DONT_THROW).getDecimals();
    }

    @Override
    public long balanceOf(Address address, Address token) {
        final var tokenRelKey = new TokenRelationshipKey(token, address);
        return store.getTokenRelationship(tokenRelKey, OnMissing.DONT_THROW).getBalance();
    }

    @Override
    public long staticAllowanceOf(final Address owner, final Address spender, final Address token) {
        final var tokenNum = EntityNum.fromEvmAddress(token);

        final var resolvedSpender = aliases.resolveForEvm(spender);
        final var spenderNum = EntityNum.fromEvmAddress(resolvedSpender);

        final var fcTokenAllowanceId = new FcTokenAllowanceId(tokenNum, spenderNum);

        final var account = store.getAccount(owner, OnMissing.DONT_THROW);
        return account.getFungibleTokenAllowances().getOrDefault(fcTokenAllowanceId, 0L);
    }

    @Override
    public Address staticApprovedSpenderOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(nftIdFromEntityId(entityId, serialNo), OnMissing.DONT_THROW);

        if (nft.isEmptyUniqueToken()) {
            return Address.ZERO;
        }
        final var spender = nft.getSpender();
        if (spender == null) {
            return Address.ZERO;
        }
        return canonicalAddress(spender.asEvmAddress());
    }

    @Override
    public boolean staticIsOperator(final Address owner, final Address operator, final Address token) {
        final var tokenNum = EntityNum.fromEvmAddress(token);
        final var operatorNum = EntityNum.fromEvmAddress(aliases.resolveForEvm(operator));

        final var fcTokenAllowanceId = new FcTokenAllowanceId(tokenNum, operatorNum);

        final var account = store.getAccount(owner, OnMissing.DONT_THROW);
        if (account.isEmptyAccount()) return false;
        return account.getApproveForAllNfts().contains(fcTokenAllowanceId);
    }

    @Override
    public Address ownerOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(nftIdFromEntityId(entityId, serialNo), OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return Address.ZERO;
        }
        final var owner = nft.getOwner();

        if (owner == null) {
            return Address.ZERO;
        }
        // redirect executor calls the canonical address of this value
        return owner.asEvmAddress();
    }

    @Override
    public Address canonicalAddress(final Address addressOrAlias) {
        Account account = store.getAccount(addressOrAlias, OnMissing.DONT_THROW);
        if (account.isEmptyAccount()) {
            return Address.ZERO;
        }
        return account.canonicalAddress();
    }

    @Override
    public String metadataOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(nftIdFromEntityId(entityId, serialNo), OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return "";
        }
        return new String(nft.getMetadata(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] ledgerId() {
        return properties.getNetwork().getLedgerId();
    }

    private Optional<EvmTokenInfo> getTokenInfo(final Address address) {
        Token token = store.getToken(address, OnMissing.DONT_THROW);

        if (token.isEmptyToken()) {
            return Optional.empty();
        }
        final var ledgerId = ledgerId();
        final EvmTokenInfo evmTokenInfo = new EvmTokenInfo(
                ledgerId,
                token.getSupplyType().ordinal(),
                token.isDeleted(),
                token.getSymbol(),
                token.getName(),
                token.getMemo(),
                token.getTreasury().getAlias() != ByteString.EMPTY
                        ? Address.wrap(Bytes.wrap(
                                DomainUtils.toBytes(token.getTreasury().getAlias())))
                        : token.getTreasury().getAccountAddress(),
                token.getTotalSupply(),
                token.getMaxSupply(),
                token.getDecimals(),
                token.getExpiry());
        evmTokenInfo.setAutoRenewPeriod(token.getAutoRenewPeriod());
        evmTokenInfo.setDefaultFreezeStatus(token.isFrozenByDefault());
        evmTokenInfo.setCustomFees(getCustomFees(address));
        setEvmKeys(token, evmTokenInfo);
        final var isPaused = token.isPaused();
        evmTokenInfo.setIsPaused(isPaused);

        if (token.getAutoRenewAccount() != null) {
            evmTokenInfo.setAutoRenewAccount(
                    token.getAutoRenewAccount().getAlias() != ByteString.EMPTY
                            ? Address.wrap(Bytes.wrap(DomainUtils.toBytes(
                                    token.getAutoRenewAccount().getAlias())))
                            : token.getAutoRenewAccount().getAccountAddress());
        }

        return Optional.of(evmTokenInfo);
    }

    @SuppressWarnings("unchecked")
    private List<CustomFee> getCustomFees(final Address address) {
        return store.getToken(address, OnMissing.THROW).getCustomFees();
    }

    private void setEvmKeys(final Token token, final EvmTokenInfo evmTokenInfo) {
        try {
            final var adminKey = evmKey(asKeyUnchecked(token.getAdminKey()).toByteArray());
            final var kycKey = evmKey(asKeyUnchecked(token.getKycKey()).toByteArray());
            final var supplyKey = evmKey(asKeyUnchecked(token.getSupplyKey()).toByteArray());
            final var freezeKey = evmKey(asKeyUnchecked(token.getFreezeKey()).toByteArray());
            final var wipeKey = evmKey(asKeyUnchecked(token.getWipeKey()).toByteArray());
            final var pauseKey = evmKey(asKeyUnchecked(token.getPauseKey()).toByteArray());
            final var feeScheduleKey =
                    evmKey(asKeyUnchecked(token.getFeeScheduleKey()).toByteArray());

            evmTokenInfo.setAdminKey(adminKey);
            evmTokenInfo.setKycKey(kycKey);
            evmTokenInfo.setSupplyKey(supplyKey);
            evmTokenInfo.setFreezeKey(freezeKey);
            evmTokenInfo.setWipeKey(wipeKey);
            evmTokenInfo.setPauseKey(pauseKey);
            evmTokenInfo.setFeeScheduleKey(feeScheduleKey);
        } catch (final IOException e) {
            throw new ParsingException("Error parsing token keys.");
        }
    }

    private NftId nftIdFromEntityId(final EntityId entityId, long serialNo) {
        return new NftId(entityId.getShard(), entityId.getRealm(), entityId.getNum(), serialNo);
    }
}
