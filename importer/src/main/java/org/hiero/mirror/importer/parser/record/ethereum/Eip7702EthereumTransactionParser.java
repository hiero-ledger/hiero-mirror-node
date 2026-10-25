// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.hiero.mirror.common.util.DomainUtils.fromHexQuantity;
import static org.hiero.mirror.common.util.DomainUtils.toHexQuantity;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.service.ContractBytecodeService;

@Named
final class Eip7702EthereumTransactionParser extends AbstractEthereumTransactionParser {

    public static final int EIP7702_TYPE_BYTE = 4;
    private static final byte[] EIP7702_TYPE_BYTES = Integers.toBytes(EIP7702_TYPE_BYTE);
    private static final String TRANSACTION_TYPE_NAME = "EIP7702";
    private static final int EIP7702_TYPE_RLP_ITEM_COUNT = 13;
    private static final int AUTHORIZATION_TUPLE_SIZE = 6;
    static final int MAX_AUTHORIZATION_LIST_SIZE = 1500;

    Eip7702EthereumTransactionParser(
            ContractBytecodeService contractBytecodeService, FileDataRepository fileDataRepository) {
        super(contractBytecodeService, fileDataRepository);
    }

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        final var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        final var type = decoder.next().asByte();
        if (type != EIP7702_TYPE_BYTE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format("First byte was %s but should be %s", type, EIP7702_TYPE_BYTE));
        }

        final var eip7702RlpItem = decoder.next();
        if (!eip7702RlpItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Second RLPItem was not a list");
        }

        final var rlpItems = eip7702RlpItem.asRLPList().elements();
        if (rlpItems.size() != EIP7702_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLP list size was %d but expected %d", rlpItems.size(), EIP7702_TYPE_RLP_ITEM_COUNT));
        }

        final var authorizationList = decodeAuthorizationList(rlpItems.get(9));

        final var ethereumTransaction = EthereumTransaction.builder()
                .chainId(rlpItems.get(0).data())
                .nonce(rlpItems.get(1).asLong())
                .maxPriorityFeePerGas(rlpItems.get(2).data())
                .maxFeePerGas(rlpItems.get(3).data())
                .gasLimit(rlpItems.get(4).asLong())
                .toAddress(rlpItems.get(5).data())
                .value(rlpItems.get(6).asBigInt().toByteArray())
                .callData(rlpItems.get(7).data())
                .accessList(decodeAccessList(rlpItems.get(8), TRANSACTION_TYPE_NAME))
                .authorizationList(authorizationList)
                .recoveryId((int) rlpItems.get(10).asByte())
                .signatureR(rlpItems.get(11).data())
                .signatureS(rlpItems.get(12).data())
                .type(EIP7702_TYPE_BYTE);

        return ethereumTransaction.build();
    }

    private List<Authorization> decodeAuthorizationList(RLPItem authorizationListItem) {
        if (!authorizationListItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Authorization list is not a list");
        }

        final var authorizationTuples = authorizationListItem.asRLPList().elements();
        if (authorizationTuples.size() > MAX_AUTHORIZATION_LIST_SIZE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "Authorization list size was %d but expected at most %d",
                            authorizationTuples.size(), MAX_AUTHORIZATION_LIST_SIZE));
        }

        final var authorizations = new ArrayList<Authorization>(authorizationTuples.size());
        for (var tupleItem : authorizationTuples) {
            authorizations.add(decodeAuthorization(tupleItem));
        }

        return authorizations;
    }

    private Authorization decodeAuthorization(RLPItem tupleItem) {
        if (!tupleItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Authorization tuple is not a list");
        }

        var tuple = tupleItem.asRLPList().elements();
        if (tuple.size() != AUTHORIZATION_TUPLE_SIZE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "Authorization tuple size was %d but expected %d", tuple.size(), AUTHORIZATION_TUPLE_SIZE));
        }

        return Authorization.builder()
                .chainId(toHexQuantity(tuple.get(0).data()))
                .address(toHex(tuple.get(1).data()))
                .nonce(authorizationNonce(tuple.get(2)))
                .yParity(toHexQuantity(tuple.get(3).data()))
                .r(toHex(tuple.get(4).data()))
                .s(toHex(tuple.get(5).data()))
                .build();
    }

    @Override
    protected byte[] encode(EthereumTransaction ethereumTransaction) {
        return RLPEncoder.sequence(
                EIP7702_TYPE_BYTES,
                List.of(
                        ethereumTransaction.getChainId(),
                        Integers.toBytes(ethereumTransaction.getNonce()),
                        ethereumTransaction.getMaxPriorityFeePerGas(),
                        ethereumTransaction.getMaxFeePerGas(),
                        Integers.toBytes(ethereumTransaction.getGasLimit()),
                        ethereumTransaction.getToAddress(),
                        getValue(ethereumTransaction),
                        ethereumTransaction.getCallData(),
                        encodeAccessList(ethereumTransaction.getAccessList()),
                        encodeAuthorizationList(ethereumTransaction.getAuthorizationList()),
                        Integers.toBytes(ethereumTransaction.getRecoveryId()),
                        ethereumTransaction.getSignatureR(),
                        ethereumTransaction.getSignatureS()));
    }

    private List<List<byte[]>> encodeAuthorizationList(final List<Authorization> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            return List.of();
        }

        var encodedList = new ArrayList<List<byte[]>>(authorizations.size());
        for (var auth : authorizations) {
            encodedList.add(encodeAuthorization(auth));
        }
        return encodedList;
    }

    private List<byte[]> encodeAuthorization(final Authorization authorization) {
        return List.of(
                fromHexQuantity(authorization.getChainId()),
                fromHex(authorization.getAddress()),
                Integers.toBytes(authorization.getNonce()),
                fromHexQuantity(authorization.getYParity()),
                fromHex(authorization.getR()),
                fromHex(authorization.getS()));
    }

    private String toHex(final byte[] data) {
        return HEX_PREFIX + HexFormat.of().formatHex(data);
    }

    private byte[] fromHex(final String hex) {
        var stripped = stripHexPrefix(hex);
        if (stripped.length() % 2 != 0) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Invalid hex string: " + hex);
        }
        return HexFormat.of().parseHex(stripped);
    }

    private static String stripHexPrefix(final String hex) {
        return hex.startsWith(HEX_PREFIX) ? hex.substring(HEX_PREFIX.length()) : hex;
    }

    private long authorizationNonce(final RLPItem nonce) {
        try {
            return nonce.asLong();
        } catch (IllegalArgumentException e) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME, "Authorization nonce is not a canonical integer");
        }
    }
}
