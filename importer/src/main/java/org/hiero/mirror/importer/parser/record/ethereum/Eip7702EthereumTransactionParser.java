// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
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

        final var authorizationList = parseAuthorizationList(rlpItems.get(9));

        final var ethereumTransaction = EthereumTransaction.builder()
                .chainId(rlpItems.get(0).data())
                .nonce(rlpItems.get(1).asLong())
                .maxPriorityFeePerGas(rlpItems.get(2).data())
                .maxFeePerGas(rlpItems.get(3).data())
                .gasLimit(rlpItems.get(4).asLong())
                .toAddress(rlpItems.get(5).data())
                .value(rlpItems.get(6).asBigInt().toByteArray())
                .callData(rlpItems.get(7).data())
                .accessList(parseAccessList(rlpItems.get(8), TRANSACTION_TYPE_NAME))
                .authorizationList(authorizationList)
                .recoveryId((int) rlpItems.get(10).asByte())
                .signatureR(rlpItems.get(11).data())
                .signatureS(rlpItems.get(12).data())
                .type(EIP7702_TYPE_BYTE);

        return ethereumTransaction.build();
    }

    private List<Authorization> parseAuthorizationList(RLPItem authorizationListItem) {
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

        var authorizations = new ArrayList<Authorization>();

        for (var tupleItem : authorizationTuples) {
            if (!tupleItem.isList()) {
                throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Authorization tuple is not a list");
            }

            var tuple = tupleItem.asRLPList().elements();
            if (tuple.size() != AUTHORIZATION_TUPLE_SIZE) {
                throw new InvalidEthereumBytesException(
                        TRANSACTION_TYPE_NAME,
                        String.format(
                                "Authorization tuple size was %d but expected %d",
                                tuple.size(), AUTHORIZATION_TUPLE_SIZE));
            }

            final var hexFormat = HexFormat.of();
            final var authorization = Authorization.builder()
                    .chainId(toQuantity(tuple.get(0).data()))
                    .address(HEX_PREFIX + hexFormat.formatHex(tuple.get(1).data()))
                    .nonce(authorizationNonce(tuple.get(2)))
                    .yParity(toQuantity(tuple.get(3).data()))
                    .r(HEX_PREFIX + hexFormat.formatHex(tuple.get(4).data()))
                    .s(HEX_PREFIX + hexFormat.formatHex(tuple.get(5).data()))
                    .build();

            authorizations.add(authorization);
        }

        return authorizations;
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

    private List<List<byte[]>> encodeAuthorizationList(List<Authorization> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            return List.of();
        }

        var encodedList = new ArrayList<List<byte[]>>();
        for (var auth : authorizations) {
            encodedList.add(List.of(
                    encodeQuantity(auth.getChainId()),
                    decodeBytes(auth.getAddress()),
                    Integers.toBytes(auth.getNonce()),
                    encodeQuantity(auth.getYParity()),
                    decodeBytes(auth.getR()),
                    decodeBytes(auth.getS())));
        }
        return encodedList;
    }

    private byte[] decodeBytes(String hex) {
        var stripped = stripHexPrefix(hex);
        if (stripped.length() % 2 != 0) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Invalid hex string: " + hex);
        }
        return decodeEvenHex(stripped, hex);
    }

    private byte[] decodeHex(String hex) {
        var stripped = stripHexPrefix(hex);
        if (stripped.length() % 2 != 0) {
            stripped = "0" + stripped;
        }
        return decodeEvenHex(stripped, hex);
    }

    private static String stripHexPrefix(String hex) {
        return hex.startsWith(HEX_PREFIX) ? hex.substring(HEX_PREFIX.length()) : hex;
    }

    private byte[] decodeEvenHex(String stripped, String hex) {
        try {
            return Hex.decodeHex(stripped);
        } catch (Exception e) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Invalid hex string: " + hex);
        }
    }

    private long authorizationNonce(RLPItem nonce) {
        try {
            return nonce.asLong();
        } catch (IllegalArgumentException e) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME, "Authorization nonce is not a canonical integer");
        }
    }

    /**
     * Canonical integer 0 is {@code "0x0"} and encodes as an empty RLP string. A payload that starts with {@code 0x00}
     * keeps those bytes, so an empty chain id and a chain id of {@code 0x00} do not collapse.
     */
    private String toQuantity(byte[] data) {
        if (ArrayUtils.isEmpty(data)) {
            return HEX_PREFIX + "0";
        }
        if (data[0] == 0) {
            return HEX_PREFIX + HexFormat.of().formatHex(data);
        }
        return HEX_PREFIX + new BigInteger(1, data).toString(16);
    }

    private byte[] encodeQuantity(String hex) {
        var stripped = hex.startsWith(HEX_PREFIX) ? hex.substring(HEX_PREFIX.length()) : hex;
        if (stripped.isEmpty() || stripped.equals("0")) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
        return decodeHex(hex);
    }
}
