// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.graphql.repository.EntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final Base32 base32 = new Base32();
    private static final int EVM_ADDRESS_BYTE_LENGTH = 20;

    @Mock
    private EntityRepository entityRepository;

    @InjectMocks
    private EntityServiceImpl entityService;

    @AfterEach
    void resetCommonProperties() {
        final var commonProperties = CommonProperties.getInstance();
        commonProperties.setShard(0);
        commonProperties.setRealm(0);
    }

    @Test
    void getByIdAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByAliasAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByEvmAddressAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(entity.getEvmAddress()), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByIdAsEvmAddressAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getId());
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByIdAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), EntityType.CONTRACT))
                .isEmpty();
    }

    @Test
    void getByAliasAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByAlias(entity.getAlias())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), EntityType.CONTRACT))
                .isEmpty();
    }

    @Test
    void getByEvmAddressAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByEvmAddress(entity.getEvmAddress())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(
                        Hex.encodeHexString(entity.getEvmAddress()), EntityType.CONTRACT))
                .isEmpty();
    }

    @Test
    void getByIdAsEvmAddressAndTypeMismatch() {

        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getNum());
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer), EntityType.CONTRACT))
                .isEmpty();
    }

    @Test
    void getByIdAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void getByAliasAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByAlias(entity.getAlias())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), entity.getType()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void getByEvmAddressAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByEvmAddress(entity.getEvmAddress())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(entity.getEvmAddress()), entity.getType()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void getByEvmAddressLookAlikeEntityAndTypeFound() {
        var entity = domainBuilder.entity().get();
        int integerStringLength = Integer.BYTES * 2;
        String padZero = StringUtils.repeat('0', integerStringLength);
        String evmAddress =
                padZero + Hex.encodeHexString(entity.getEvmAddress()).substring(integerStringLength);
        try {
            when(entityRepository.findByEvmAddress(Hex.decodeHex(evmAddress))).thenReturn(Optional.of(entity));
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        assertThat(entityService.getByEvmAddressAndType(evmAddress, entity.getType()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void getByIdAsEvmAddressAndTypeFound() {
        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getNum());
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer), entity.getType()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void getByEvmAddressWithNumBeyondMaxTreatedAsOpaque() {
        // A long-zero shaped address whose trailing long embeds realm/shard bits (num > 2^38-1) must not resolve
        // by raw encoded id
        final var entity = domainBuilder.entity().get();
        final var evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        final long craftedTrailingLong = (1L << 38) + 5;
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, craftedTrailingLong);
        when(entityRepository.findByEvmAddress(evmBuffer.array())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer.array()), entity.getType()))
                .get()
                .isEqualTo(entity);
        verify(entityRepository, never()).findById(craftedTrailingLong);
    }

    @Test
    void getByLongZeroEvmAddressAppliesConfiguredShardRealm() {
        // On a deployment with non-zero shard/realm, a canonical long-zero address must resolve to the encoded id
        // formed by combining the trailing num with the configured shard/realm, not the raw trailing long.
        final var commonProperties = CommonProperties.getInstance();
        commonProperties.setShard(1);
        commonProperties.setRealm(2);
        final long num = 5;
        final var entity = domainBuilder.entity().get();
        final long encodedId = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num)
                .getId();
        final var evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, num);
        when(entityRepository.findById(encodedId)).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer.array()), entity.getType()))
                .get()
                .isEqualTo(entity);
        verify(entityRepository, never()).findById(num);
    }
}
