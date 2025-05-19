// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import java.util.Arrays;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied model from hedera-services.
 * <p>
 * Encapsulates the state and operations of a Hedera Unique token.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs. This model is used as a value in a special state, used for speculative write
 * operations.
 * <p>
 * Differences from the original:
 *  1. Added address field for convenience
 *  2. Added factory method that returns empty instance
 *  3. Added equals() and hashCode()
 *  4. Added isEmptyUniqueToken()
 */
public class UniqueToken {
    private final Id tokenId;
    private final Address address;
    private final long serialNumber;
    private final RichInstant creationTime;
    private final Id owner;
    private final Id spender;
    private final byte[] metadata;
    private final NftId nftId;

    public UniqueToken(Id tokenId, long serialNumber, RichInstant creationTime, Id owner, Id spender, byte[] metadata) {
        this.tokenId = tokenId;
        this.address = tokenId.asEvmAddress();
        this.serialNumber = serialNumber;
        this.creationTime = creationTime;
        this.owner = owner;
        this.spender = spender;
        this.metadata = metadata;
        this.nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNumber);
    }

    public static UniqueToken getEmptyUniqueToken() {
        return new UniqueToken(Id.DEFAULT, 0L, RichInstant.MISSING_INSTANT, Id.DEFAULT, Id.DEFAULT, new byte[0]);
    }

    private UniqueToken createNewUniqueTokenWithNewOwner(UniqueToken oldUniqueToken, Id newOwner) {
        return new UniqueToken(
                oldUniqueToken.tokenId,
                oldUniqueToken.serialNumber,
                oldUniqueToken.creationTime,
                newOwner,
                oldUniqueToken.spender,
                oldUniqueToken.metadata);
    }

    /**
     * Creates new instance of {@link UniqueToken} with updated spender in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldUniqueToken
     * @param newSpender
     * @return the new instance of {@link UniqueToken} with updated {@link #spender} property
     */
    private UniqueToken createNewUniqueTokenWithNewSpender(UniqueToken oldUniqueToken, Id newSpender) {
        return new UniqueToken(
                oldUniqueToken.tokenId,
                oldUniqueToken.serialNumber,
                oldUniqueToken.creationTime,
                oldUniqueToken.owner,
                newSpender,
                oldUniqueToken.metadata);
    }

    public boolean isEmptyUniqueToken() {
        return this.equals(getEmptyUniqueToken());
    }

    public NftId getNftId() {
        return nftId;
    }

    public Id getTokenId() {
        return tokenId;
    }

    public Address getAddress() {
        return address;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public RichInstant getCreationTime() {
        return creationTime;
    }

    public Id getOwner() {
        return owner;
    }

    public UniqueToken setOwner(Id newOwner) {
        return createNewUniqueTokenWithNewOwner(this, newOwner);
    }

    public Id getSpender() {
        return spender;
    }

    public UniqueToken setSpender(Id spender) {
        return createNewUniqueTokenWithNewSpender(this, spender);
    }

    public byte[] getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("tokenID", tokenId)
                .add("serialNum", serialNumber)
                .add("metadata", metadata)
                .add("creationTime", creationTime)
                .add("owner", owner)
                .add("spender", spender)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UniqueToken that = (UniqueToken) o;
        return serialNumber == that.serialNumber
                && Objects.equals(tokenId, that.tokenId)
                && Objects.equals(address, that.address)
                && Objects.equals(creationTime, that.creationTime)
                && Objects.equals(owner, that.owner)
                && Objects.equals(spender, that.spender)
                && Arrays.equals(metadata, that.metadata)
                && Objects.equals(nftId, that.nftId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tokenId, address, serialNumber, creationTime, owner, spender, nftId);
        result = 31 * result + Arrays.hashCode(metadata);
        return result;
    }
}
