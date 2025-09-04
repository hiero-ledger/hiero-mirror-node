// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class EntityRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    private final EntityRepository entityRepository;

    @Test
    void nullCharacter() {
        Entity entity =
                domainBuilder.entity().customize(e -> e.memo("abc" + (char) 0)).persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void publicKeyUpdates() {
        var entity = domainBuilder.entity().customize(b -> b.key(null)).persist();

        // unset key should result in null public key
        assertThat(entityRepository.findById(entity.getId())).get().returns(null, Entity::getPublicKey);

        // default proto key of single byte should result in empty public key
        entity.setKey(Key.getDefaultInstance().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get().returns(StringUtils.EMPTY, Entity::getPublicKey);

        // invalid key should set public key to the sentinel value
        entity.setKey("123".getBytes());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get().returns(StringUtils.EMPTY, Entity::getPublicKey);

        // 1 out of 2 threshold key should set public key to the sentinel value
        entity.setKey(domainBuilder.thresholdKey(2, 1));
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get().returns(StringUtils.EMPTY, Entity::getPublicKey);

        // valid key should not be null
        entity.setKey(Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("123"))
                .build()
                .toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNotNull();

        // null key like unset should result in null public key
        entity.setKey(null);
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNull();
    }

    /**
     * This test verifies that the Entity domain object and table definition are in sync with the entity_history table.
     */
    @Test
    void history() {
        Entity entity = domainBuilder.entity().persist();

        jdbcOperations.update("insert into entity_history select * from entity");
        List<Entity> entityHistory = jdbcOperations.query("select * from entity_history", ROW_MAPPER);

        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(entityHistory).containsExactly(entity);
    }

    @Test
    void findByAlias() {
        Entity entity = domainBuilder.entity().persist();
        byte[] alias = entity.getAlias();

        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        var entityDeleted =
                domainBuilder.entity().customize(b -> b.deleted(true)).persist();

        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity.getId());
        assertThat(entityRepository.findByEvmAddress(entityDeleted.getEvmAddress()))
                .isEmpty();
        assertThat(entityRepository.findByEvmAddress(new byte[] {1, 2, 3})).isEmpty();
    }

    @Test
    void findById() {
        var entity = domainBuilder.entity(-2, domainBuilder.timestamp()).persist();
        assertThat(entityRepository.findById(entity.getId())).contains(entity);
    }

    @Test
    void updateContractType() {
        Entity entity = domainBuilder.entity().persist();
        Entity entity2 = domainBuilder.entity().persist();
        entityRepository.updateContractType(List.of(entity.getId(), entity2.getId()));
        assertThat(entityRepository.findAll())
                .hasSize(2)
                .extracting(Entity::getType)
                .allMatch(e -> e == CONTRACT);
    }

    @Test
    void findEvmAddressByIds() {
        final var entity = domainBuilder.entity().persist();
        final var entity2 = domainBuilder.entity().persist();
        final var noEvmAddressEntity =
                domainBuilder.entity().customize(e -> e.evmAddress(null)).persist();

        final var results = entityRepository.findEvmAddressesByIds(
                List.of(entity.getId(), entity2.getId(), noEvmAddressEntity.getId()));
        assertThat(results)
                .hasSize(2)
                .anySatisfy(e -> {
                    assertThat(e.getId()).isEqualTo(entity.getId());
                    assertThat(e.getEvmAddress()).isEqualTo(entity.getEvmAddress());
                })
                .anySatisfy(e -> {
                    assertThat(e.getId()).isEqualTo(entity2.getId());
                    assertThat(e.getEvmAddress()).isEqualTo(entity2.getEvmAddress());
                });
    }
}
