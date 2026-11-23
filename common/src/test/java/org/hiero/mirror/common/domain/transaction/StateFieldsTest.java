// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateFields.ACCOUNT_ETHEREUM_NONCE;
import static org.hiero.mirror.common.domain.transaction.StateFields.CLPR_ENDPOINT_MANIFEST_VERSION;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Internal;
import com.google.protobuf.Message;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.ClprEndpointManifest;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class StateFieldsTest {

    @Test
    void allFieldsAreStateFields() {
        for (final var field : StateFields.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) {
                continue;
            }

            // A concrete message type argument, e.g. StateField<Account>, lets isSet() build the message
            assertThat(field.getType()).as(field.getName()).isEqualTo(StateField.class);
            assertThat(field.getGenericType()).as(field.getName()).isInstanceOf(ParameterizedType.class);
            assertThat(((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0])
                    .as(field.getName())
                    .isInstanceOf(Class.class);
        }

        assertThat(stateFields()).isNotEmpty();
    }

    @Test
    void isPresent() {
        final var full = MapUpdateChange.getDefaultInstance();
        final var partial = MapUpdateChange.newBuilder().setPartial(true).build();
        final var cleared = partial.toBuilder()
                .addClearedFields(Account.ETHEREUM_NONCE_FIELD_NUMBER)
                .build();
        final var account = Account.newBuilder().setEthereumNonce(1).build();
        final var unset = Account.getDefaultInstance();

        assertThat(ACCOUNT_ETHEREUM_NONCE.isPresent(full, unset)).isTrue();
        assertThat(ACCOUNT_ETHEREUM_NONCE.isPresent(partial, account)).isTrue();
        assertThat(ACCOUNT_ETHEREUM_NONCE.isPresent(cleared, unset)).isTrue();
        assertThat(ACCOUNT_ETHEREUM_NONCE.isPresent(partial, unset)).isFalse();
    }

    @Test
    void isPresentSingleton() {
        final var full = SingletonUpdateChange.getDefaultInstance();
        final var partial = SingletonUpdateChange.newBuilder().setPartial(true).build();
        final var cleared = partial.toBuilder()
                .addClearedFields(ClprEndpointManifest.VERSION_FIELD_NUMBER)
                .build();
        final var manifest = ClprEndpointManifest.newBuilder().setVersion(1).build();
        final var unset = ClprEndpointManifest.getDefaultInstance();

        assertThat(CLPR_ENDPOINT_MANIFEST_VERSION.isPresent(full, unset)).isTrue();
        assertThat(CLPR_ENDPOINT_MANIFEST_VERSION.isPresent(partial, manifest)).isTrue();
        assertThat(CLPR_ENDPOINT_MANIFEST_VERSION.isPresent(cleared, unset)).isTrue();
        assertThat(CLPR_ENDPOINT_MANIFEST_VERSION.isPresent(partial, unset)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateFields")
    void isSet(final String name, final StateField<Message> field, final Message defaultInstance) {
        final var descriptor = defaultInstance.getDescriptorForType();
        final var target = descriptor.findFieldByNumber(field.fieldNumber());
        assertThat(target)
                .as("field number %d of %s", field.fieldNumber(), descriptor.getName())
                .isNotNull();
        assertThat(field.isSet().test(defaultInstance)).isFalse();

        for (final var other : descriptor.getFields()) {
            assertThat(field.isSet().test(withOnly(defaultInstance, other)))
                    .as("%s with only %s set", descriptor.getName(), other.getName())
                    .isEqualTo(other.equals(target));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Arguments> stateFields() {
        final var arguments = new ArrayList<Arguments>();
        for (final var field : StateFields.class.getDeclaredFields()) {
            if (field.getType() != StateField.class) {
                continue;
            }

            // The message type is erased from the record at runtime, but kept in the field's declared generic type
            final var type = (ParameterizedType) field.getGenericType();
            final var messageClass = (Class<? extends Message>) type.getActualTypeArguments()[0];
            try {
                arguments.add(
                        Arguments.of(field.getName(), field.get(null), Internal.getDefaultInstance(messageClass)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        return arguments;
    }

    private static Message withOnly(final Message defaultInstance, final FieldDescriptor field) {
        final var builder = defaultInstance.newBuilderForType();
        final var value = sample(builder, field);
        if (field.isRepeated()) {
            builder.addRepeatedField(field, value);
        } else {
            builder.setField(field, value);
        }

        return builder.build();
    }

    /**
     * A value that puts the field on the wire. A field with presence (message, oneof member, optional) gets the default
     * value, so a check comparing against the default fails the test.
     */
    private static Object sample(final Message.Builder builder, final FieldDescriptor field) {
        if (field.getJavaType() == JavaType.MESSAGE) {
            return builder.newBuilderForField(field).build();
        }

        if (field.hasPresence()) {
            return field.getDefaultValue();
        }

        return switch (field.getJavaType()) {
            case BOOLEAN -> true;
            case BYTE_STRING -> ByteString.copyFromUtf8("a");
            case DOUBLE -> 1.0d;
            case ENUM -> field.getEnumType().findValueByNumberCreatingIfUnknown(1);
            case FLOAT -> 1.0f;
            case INT -> 1;
            case LONG -> 1L;
            case MESSAGE -> throw new IllegalStateException("Unexpected message field " + field.getFullName());
            case STRING -> "a";
        };
    }
}
