// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.util.Utility.HALT_ON_ERROR_DEFAULT;
import static org.hiero.mirror.importer.util.Utility.HALT_ON_ERROR_PROPERTY;
import static org.hiero.mirror.importer.util.Utility.RECOVERABLE_ERROR;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.ParserException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SuppressWarnings("java:S5786")
@ExtendWith(OutputCaptureExtension.class)
public class UtilityTest {

    public static final byte[] ALIAS_ECDSA_SECP256K1 =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    public static final byte[] EVM_ADDRESS = Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    @AfterEach
    void cleanup() {
        System.setProperty(HALT_ON_ERROR_PROPERTY, HALT_ON_ERROR_DEFAULT);
    }

    @Test
    void aliasToEvmAddress() {
        byte[] aliasEd25519 = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("ab"))
                .build()
                .toByteArray();
        byte[] aliasEcdsa2 = Hex.decode("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
        byte[] aliasInvalidEcdsa = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(TestUtils.generateRandomByteArray(33)))
                .build()
                .toByteArray();
        byte[] aliasUncompressedEcdsa = Hex.decode(
                "3a374349514637575a4f55475132415336334d504757573633484750484b584b5442344f3643505a334b3437374133354b584257525a4e4941");
        byte[] evmAddress2 = Hex.decode("efa0d905af20199aa03aca71cfa5f7647f29f439");
        byte[] randomBytes = TestUtils.generateRandomByteArray(DomainUtils.EVM_ADDRESS_LENGTH);
        byte[] tooShortBytes = TestUtils.generateRandomByteArray(32);
        byte[] invalidBytes = Bytes.concat(new byte[] {'a'}, TestUtils.generateRandomByteArray(32));

        assertThat(Utility.aliasToEvmAddress(ALIAS_ECDSA_SECP256K1)).isEqualTo(EVM_ADDRESS);
        assertThat(Utility.aliasToEvmAddress(aliasEcdsa2)).isEqualTo(evmAddress2);
        assertThat(Utility.aliasToEvmAddress(randomBytes)).isEqualTo(randomBytes);
        assertThat(Utility.aliasToEvmAddress(aliasInvalidEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasUncompressedEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasEd25519)).isNull();
        assertThat(Utility.aliasToEvmAddress(null)).isNull();
        assertThat(Utility.aliasToEvmAddress(new byte[] {})).isNull();
        assertThat(Utility.aliasToEvmAddress(tooShortBytes)).isNull();
        assertThat(Utility.aliasToEvmAddress(invalidBytes)).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"0,0", "86400000000000,1", "1653487416000000000,19137"})
    void getEpochDay(long timestamp, long expected) {
        assertThat(Utility.getEpochDay(timestamp)).isEqualTo(expected);
    }

    @Test
    void getTopic() {
        ContractLoginfo contractLoginfo = ContractLoginfo.newBuilder()
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 1}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 127}))
                .addTopic(ByteString.copyFrom(new byte[] {-1}))
                .addTopic(ByteString.copyFrom(new byte[] {0}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0}))
                .addTopic(ByteString.copyFrom(new byte[0]))
                .build();
        assertThat(Utility.getTopic(contractLoginfo, 0)).isEqualTo(new byte[] {1});
        assertThat(Utility.getTopic(contractLoginfo, 1)).isEqualTo(new byte[] {127});
        assertThat(Utility.getTopic(contractLoginfo, 2)).isEqualTo(new byte[] {-1});
        assertThat(Utility.getTopic(contractLoginfo, 3)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 4)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 5)).isEmpty();
        assertThat(Utility.getTopic(contractLoginfo, 999)).isNull();
    }

    @Test
    @DisplayName("get TransactionId")
    void getTransactionID() {
        AccountID payerAccountId = AccountID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setAccountNum(2)
                .build();
        TransactionID transactionId = Utility.getTransactionId(payerAccountId);
        assertThat(transactionId).isNotEqualTo(TransactionID.getDefaultInstance());

        AccountID testAccountId = transactionId.getAccountID();
        assertAll(
                // row counts
                () -> assertEquals(payerAccountId.getShardNum(), testAccountId.getShardNum()),
                () -> assertEquals(payerAccountId.getRealmNum(), testAccountId.getRealmNum()),
                () -> assertEquals(payerAccountId.getAccountNum(), testAccountId.getAccountNum()));
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({"1569936354, 901", "0, 901", "1569936354, 0", "0,0"})
    void instantToTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds).plusNanos(nanos);
        Timestamp test = Utility.instantToTimestamp(instant);
        assertAll(
                () -> assertEquals(instant.getEpochSecond(), test.getSeconds()),
                () -> assertEquals(instant.getNano(), test.getNanos()));
    }

    @ParameterizedTest(name = "Convert {0} to snake case")
    @CsvSource({",", "\"\",\"\"", "Foo,foo", "FooBar,foo_bar", "foo_bar,foo_bar"})
    void toSnakeCase(String input, String output) {
        assertThat(Utility.toSnakeCase(input)).isEqualTo(output);
    }

    @ParameterizedTest(name = "Log format {0} with params {1} expecting {2}")
    @CsvSource(textBlock = """
                                plain message, , plain message,
                                {} arg message, 'one, exception', one arg message, java.lang.RuntimeException: provided cause
                                {} {} message, 'a, b', a b message,
                                {} {} {} {}, 'a, b, c, d, exception', a b c d, java.lang.RuntimeException: provided cause
                            """)
    void handleRecoverableErrorLogOrThrow(
            String format,
            @ConvertWith(ObjectArrayConverter.class) Object[] args,
            String formatted,
            String causeLogOutput,
            CapturedOutput capturedOutput) {

        var causeProvided = args.length > 0 && args[args.length - 1] instanceof Throwable throwable ? throwable : null;

        System.setProperty(HALT_ON_ERROR_PROPERTY, "false");
        Utility.handleRecoverableError(format, args);
        var allOutput = capturedOutput.getAll();
        assertThat(allOutput).contains(RECOVERABLE_ERROR + formatted);
        if (causeProvided != null) {
            assertThat(allOutput).contains(causeLogOutput);
        }

        System.setProperty(HALT_ON_ERROR_PROPERTY, "true");
        assertThatThrownBy(() -> Utility.handleRecoverableError(format, args))
                .isInstanceOf(ParserException.class)
                .hasMessage(formatted)
                .hasCause(causeProvided);
    }

    private static class ObjectArrayConverter extends SimpleArgumentConverter {

        private static final String CAUSE_ARGUMENT = "exception";
        private static final Throwable CAUSE_INSTANCE = new RuntimeException("provided cause");
        private static final Object[] EMPTY_ARGS_INSTANCE = new Object[0];

        @Override
        protected Object convert(Object source, Class<?> targetType) throws ArgumentConversionException {
            if (source == null) {
                return EMPTY_ARGS_INSTANCE;
            }

            if (source instanceof String sourceStr && Object[].class.isAssignableFrom(targetType)) {
                var stringArgs = sourceStr.split("\\s*,\\s*");
                var argsLength = stringArgs.length;
                if (argsLength > 0 && stringArgs[argsLength - 1].equals(CAUSE_ARGUMENT)) {
                    List<Object> arrList = new ArrayList<>(Arrays.asList(stringArgs));
                    arrList.set(argsLength - 1, CAUSE_INSTANCE);
                    return arrList.toArray();
                }
                return stringArgs;
            } else {
                throw new ArgumentConversionException(
                        "Conversion from " + source.getClass() + " to " + targetType + " not supported.");
            }
        }
    }
}
