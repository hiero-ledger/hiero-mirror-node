// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.util;

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.exception.ParserException;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;
import org.slf4j.helpers.MessageFormatter;

@CustomLog
@UtilityClass
public class Utility {

    // Blockstreams no longer contain runningHashVersion, this is the latest version
    public static final long DEFAULT_RUNNING_HASH_VERSION = 3;
    public static final Instant MAX_INSTANT_LONG = Instant.ofEpochSecond(0, Long.MAX_VALUE);
    public static final String HALT_ON_ERROR_PROPERTY = "HIERO_MIRROR_IMPORTER_PARSER_HALTONERROR";
    public static final String HALT_ON_DOWNLOADER_ERROR_PROPERTY = "HIERO_MIRROR_IMPORTER_DOWNLOADER_HALTONERROR";
    static final String RECOVERABLE_ERROR = "Recoverable error. ";
    static final String HALT_ON_ERROR_DEFAULT = "false";
    private static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;

    /**
     * Converts an ECDSA secp256k1 alias to a 20 byte EVM address by taking the keccak hash of it. Logic copied from
     * services' AliasManager.
     *
     * @param alias the bytes representing a serialized Key protobuf
     * @return the 20 byte EVM address
     */
    @SuppressWarnings("java:S1168")
    public static byte[] aliasToEvmAddress(byte[] alias) {
        if (alias == null
                || alias.length != DomainUtils.EVM_ADDRESS_LENGTH
                        && alias.length < ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
            return null;
        }

        if (alias.length == DomainUtils.EVM_ADDRESS_LENGTH) {
            return alias;
        }

        byte[] evmAddress = null;
        try {
            var key = Key.parseFrom(alias);
            if (key.getKeyCase() == Key.KeyCase.ECDSA_SECP256K1
                    && key.getECDSASecp256K1().size() == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                byte[] rawCompressedKey = DomainUtils.toBytes(key.getECDSASecp256K1());
                evmAddress = recoverAddressFromPubKey(rawCompressedKey);
                if (evmAddress == null) {
                    log.warn("Unable to recover EVM address from {}", Hex.encodeHexString(rawCompressedKey));
                }
            }
        } catch (Exception e) {
            var aliasHex = Hex.encodeHexString(alias);
            handleRecoverableError("Unable to decode alias to EVM address: {}", aliasHex, e);
        }

        return evmAddress;
    }

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * Decodes hex encoded bytecode with optional "0x" prefix
     *
     * @param bytecode Hex-encoded bytecode
     * @return decoded bytecode
     */
    public static byte[] decodeBytecode(byte[] bytecode) {
        return org.bouncycastle.util.encoders.Hex.decode(stripHexPrefix(bytecode));
    }

    /**
     * print a protobuf Message's content to a String
     *
     * @param message
     * @return
     */
    public static String printProtoMessage(GeneratedMessage message) {
        return TextFormat.printer().printToString(message);
    }

    public static void archiveFile(String filename, byte[] contents, Path destinationRoot) {
        Path destination = destinationRoot.resolve(filename);

        try {
            destination.getParent().toFile().mkdirs();
            Files.write(destination, contents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.trace("Archived file to {}", destination);
        } catch (Exception e) {
            log.error("Error archiving file to {}", destination, e);
            if (Boolean.parseBoolean(System.getProperty(HALT_ON_DOWNLOADER_ERROR_PROPERTY))) System.exit(-1);
        }
    }

    /**
     * Gets epoch day from the timestamp in nanos.
     *
     * @param timestamp The timestamp in nanos
     * @return The epoch day
     */
    public static long getEpochDay(long timestamp) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(0, timestamp), ZoneOffset.UTC)
                .atStartOfDay()
                .toLocalDate()
                .toEpochDay();
    }

    /**
     * Retrieves the nth topic from the contract log info or null if there is no such topic at that index. The topic is
     * returned as a byte array with leading zeros removed.
     *
     * @param contractLoginfo
     * @param index
     * @return a byte array topic with leading zeros removed or null
     */
    @SuppressWarnings("java:S1168")
    public static byte[] getTopic(ContractLoginfo contractLoginfo, int index) {
        var topics = contractLoginfo.getTopicList();
        ByteString byteString = Iterables.get(topics, index, null);

        if (byteString == null) {
            return null;
        }

        byte[] topic = DomainUtils.toBytes(byteString);
        int firstNonZero = 0;
        for (int i = 0; i < topic.length; i++) {
            if (topic[i] != 0 || i == topic.length - 1) {
                firstNonZero = i;
                break;
            }
        }
        return Arrays.copyOfRange(topic, firstNonZero, topic.length);
    }

    /**
     * Retrieves the data from the contract log info with leading zeros removed.
     * <p>
     * Data must be trimmed because ContractLoginfo stores data as a 32-byte left-padded value
     * (standard EVM ABI encoding), while SyntheticContractLog stores the minimal representation
     * without leading zeros. For accurate comparison between a synthetic log and an existing
     * contract log, both must be in the same trimmed format.
     *
     * @param contractLoginfo the contract log info
     * @return a byte array with leading zeros removed, or null if empty
     */
    @SuppressWarnings("java:S1168")
    public static byte[] getDataTrimmed(ContractLoginfo contractLoginfo) {
        ByteString byteString = contractLoginfo.getData();

        if (byteString == null || byteString.isEmpty()) {
            return null;
        }

        byte[] data = DomainUtils.toBytes(byteString);
        int firstNonZero = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != 0 || i == data.length - 1) {
                firstNonZero = i;
                break;
            }
        }
        return Arrays.copyOfRange(data, firstNonZero, data.length);
    }

    /**
     * Compares two different byte arrays
     *
     * @param actual the actual byte array to be checked
     * @param expected the expected byte array to check with
     */
    public static boolean byteArrayCompare(byte[] actual, byte[] expected) {
        if (actual.length != expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a TransactionID object
     *
     * @param payerAccountId the AccountID of the transaction payer account
     */
    public static TransactionID getTransactionId(AccountID payerAccountId) {
        Timestamp validStart = Utility.instantToTimestamp(Instant.now());
        return TransactionID.newBuilder()
                .setAccountID(payerAccountId)
                .setTransactionValidStart(validStart)
                .build();
    }

    public static String toSnakeCase(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    /**
     * Handle a parser recoverable error. Depending on the value of the system property
     * HIERO_MIRROR_IMPORTER_PARSER_HALTONERROR, when false (default), the provided message and arguments are logged at
     * ERROR level, with the message prepended with the String defined by Utility.RECOVERABLE_ERROR, identifying it as a
     * recoverable error.
     * <p/>
     * When the system property is set to true, then ParserException is thrown, and the provided message and arguments
     * are used for generating the exception message string. Nothing is logged in this mode.
     *
     * @param message The message string to be logged. This may contain ordered placeholders in the format of {} just
     *                like the rest of the logging in Mirror Node.
     * @param args    the variable arguments list to match each placeholder. Simply omit this if there are no message
     *                placeholders. The final, or only, argument may be a reference to a Throwable, in which case it is
     *                identified as the cause of the error; either logged in the stacktrace following the message, or as
     *                the cause of the thrown ParserException.
     */
    public static void handleRecoverableError(String message, Object... args) {
        var haltOnError = Boolean.parseBoolean(System.getProperty(HALT_ON_ERROR_PROPERTY));

        if (haltOnError) {
            var formattingTuple = MessageFormatter.arrayFormat(message, args);
            var throwable = formattingTuple.getThrowable();
            var formattedMessage = formattingTuple.getMessage();
            throw new ParserException(formattedMessage, throwable);
        } else {
            log.error(RECOVERABLE_ERROR + message, args);
        }
    }

    // This method is copied from consensus node's EthTxSigs::recoverAddressFromPubKey and should be kept in sync
    @SuppressWarnings("java:S1168")
    private static byte[] recoverAddressFromPubKey(byte[] pubKeyBytes) {
        LibSecp256k1.secp256k1_pubkey pubKey = new LibSecp256k1.secp256k1_pubkey();
        var parseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(CONTEXT, pubKey, pubKeyBytes, pubKeyBytes.length);
        if (parseResult == 1) {
            return recoverAddressFromPubKey(pubKey);
        } else {
            return null;
        }
    }

    // This method is copied from consensus node's EthTxSigs::recoverAddressFromPubKey and should be kept in sync
    @SuppressWarnings("java:S1191")
    private static byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
        final ByteBuffer recoveredFullKey = ByteBuffer.allocate(65);
        int value = recoveredFullKey.limit();
        final com.sun.jna.ptr.LongByReference fullKeySize = new com.sun.jna.ptr.LongByReference(value);
        LibSecp256k1.secp256k1_ec_pubkey_serialize(
                CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_UNCOMPRESSED);

        recoveredFullKey.get(); // read and discard - recoveryId is not part of the account hash
        var preHash = new byte[64];
        recoveredFullKey.get(preHash, 0, 64);
        var keyHash = new Keccak.Digest256().digest(preHash);
        var address = new byte[20];
        System.arraycopy(keyHash, 12, address, 0, 20);
        return address;
    }

    private static byte[] stripHexPrefix(byte[] data) {
        // If the first two bytes are hex prefix '0x', strip them
        if (data.length >= 2 && data[0] == (byte) 0x30 && data[1] == (byte) 0x78) {
            return ArrayUtils.subarray(data, 2, data.length);
        }

        return data;
    }
}
