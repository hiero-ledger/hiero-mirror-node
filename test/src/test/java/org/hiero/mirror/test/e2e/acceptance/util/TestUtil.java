// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;
import com.google.common.io.BaseEncoding;
import com.google.common.net.InetAddresses;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.Key;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient;
import org.hiero.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@UtilityClass
public class TestUtil {
    public static final String HEX_PREFIX = "0x";
    public static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final BaseEncoding BASE32_ENCODER = BaseEncoding.base32().omitPadding();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern extractTransactionIdPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)@(\\d+)\\.(\\d+)");

    public static String getAliasFromPublicKey(@NonNull PublicKey key) {
        if (key.isECDSA()) {
            return BASE32_ENCODER.encode(Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFrom(key.toBytesRaw()))
                    .build()
                    .toByteArray());
        } else if (key.isED25519()) {
            return BASE32_ENCODER.encode(Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(key.toBytesRaw()))
                    .build()
                    .toByteArray());
        }

        throw new IllegalStateException("Unsupported key type");
    }

    public static String to32BytesString(String data) {
        return StringUtils.leftPad(data.replace(HEX_PREFIX, ""), 64, '0');
    }

    public static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static Address asAddress(final String address) {
        return toAddressFromHex(address);
    }

    public static Address asAddress(final ExpandedAccountId accountId) {
        return toAddressFromHex(accountId.getAccountId().toEvmAddress());
    }

    public static Address asAddress(final TokenId tokenId) {
        return toAddressFromHex(tokenId.toEvmAddress());
    }

    public static Address asAddress(final ContractId contractId) {
        return toAddressFromHex(contractId.toEvmAddress());
    }

    public static Address asAddress(final AccountId accountId) {
        return toAddressFromHex(accountId.toEvmAddress());
    }

    public static Address asAddress(final ContractClient contractClient) {
        return toAddressFromHex(contractClient.getClientAddress());
    }

    public static Address asAddress(final TokenClient tokenClient) {
        return toAddressFromHex(tokenClient
                .getSdkClient()
                .getExpandedOperatorAccountId()
                .getAccountId()
                .toEvmAddress());
    }

    public static Address asAddress(final long num) {
        return Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(num)));
    }

    public static Tuple accountAmount(String account, Long amount, boolean isApproval) {
        return Tuple.of(asAddress(account), amount, isApproval);
    }

    public static Tuple nftAmount(String sender, String receiver, Long serialNumber, boolean isApproval) {
        return Tuple.of(asAddress(sender), asAddress(receiver), serialNumber, isApproval);
    }

    public static Address[] asAddressArray(List<String> addressStrings) {
        return addressStrings.stream().map(TestUtil::asAddress).toArray(Address[]::new);
    }

    public static byte[][] asByteArray(List<String> hexStringList) {
        return hexStringList.stream()
                .map(hexString -> Bytes.fromHexString(hexString).toArrayUnsafe())
                .toArray(byte[][]::new);
    }

    public static long[] asLongArray(final List<Long> longList) {
        return longList.stream().mapToLong(Long::longValue).toArray();
    }

    public static String getAbiFunctionAsJsonString(CompiledSolidityArtifact artifact, String functionName) {
        return Arrays.stream(artifact.getAbi())
                .filter(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return Objects.equals(functionName, map.get("name"));
                    }
                    return false;
                })
                .map(TestUtil::toJson)
                .findFirst()
                .orElseThrow();
    }

    public static String extractTransactionId(String message) {
        Matcher matcher = extractTransactionIdPattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        } else {
            return "Not found";
        }
    }

    public static ByteString toIpAddressV4(String host) throws UnknownHostException {
        if (!InetAddresses.isInetAddress(host)) {
            return ByteString.EMPTY;
        }

        var address = InetAddress.getByName(host).getAddress();
        return ByteString.copyFrom(address);
    }

    @SneakyThrows
    private static String toJson(Object object) {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static BigInteger hexToDecimal(String hex) {
        return Bytes32.fromHexString(hex).toBigInteger();
    }

    public static byte[] nextBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static AccountId fromSolidityAddress(String address) {
        var entityId = DomainUtils.fromEvmAddress(Bytes.fromHexString(address).toArrayUnsafe());
        var commonProperties = CommonProperties.getInstance();

        if (entityId != null
                && entityId.getShard() == commonProperties.getShard()
                && entityId.getRealm() == commonProperties.getRealm()) {
            return new AccountId(entityId.getShard(), entityId.getRealm(), entityId.getNum());
        } else {
            return AccountId.fromEvmAddress(address, commonProperties.getShard(), commonProperties.getRealm());
        }
    }

    public static Range<Instant> convertRange(org.hiero.mirror.rest.model.TimestampRange timestampRange) {
        if (timestampRange == null) {
            return null;
        }

        var from = convertTimestamp(timestampRange.getFrom());
        var to = convertTimestamp(timestampRange.getTo());

        if (from != null && to != null) {
            return Range.closedOpen(from, to);
        } else if (from != null) {
            return Range.atLeast(from);
        } else if (to != null) {
            return Range.lessThan(to);
        }

        throw new IllegalArgumentException("Unsupported TimestampRange: " + timestampRange);
    }

    public static Instant convertTimestamp(String timestamp) {
        var parts = StringUtils.split(timestamp, '.');

        if (parts == null || parts.length != 2) {
            throw new IllegalArgumentException("Unsupported Timestamp: " + timestamp);
        }

        long seconds = Long.parseLong(parts[0]);
        long nanos = Long.parseLong(parts[1]);
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static Address toAddressFromHex(final String hex) {
        final var normalized = hex.startsWith(HEX_PREFIX) ? hex : HEX_PREFIX + hex;
        final var addressBytes = Bytes.fromHexString(normalized);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static class TokenTransferListBuilder {
        private Tuple tokenTransferList;
        private Address token;

        public TokenTransferListBuilder forToken(final String token) {
            this.token = asAddress(token);
            return this;
        }

        public TokenTransferListBuilder withAccountAmounts(final Tuple... accountAmounts) {
            this.tokenTransferList = Tuple.of(token, accountAmounts, new Tuple[] {});
            return this;
        }

        public TokenTransferListBuilder withNftTransfers(final Tuple... nftTransfers) {
            this.tokenTransferList = Tuple.of(token, new Tuple[] {}, nftTransfers);
            return this;
        }

        public Tuple build() {
            return tokenTransferList;
        }
    }
}
