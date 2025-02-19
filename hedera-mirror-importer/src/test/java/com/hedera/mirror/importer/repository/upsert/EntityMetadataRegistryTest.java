/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.repository.upsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class EntityMetadataRegistryTest extends ImporterIntegrationTest {

    private final EntityMetadataRegistry registry;

    @Test
    void lookup() {
        var all = "alias,auto_renew_account_id,auto_renew_period,balance,balance_timestamp,created_timestamp,"
                + "decline_reward,deleted,ethereum_nonce,evm_address,expiration_timestamp,id,key,"
                + "max_automatic_token_associations,memo,num,obtainer_id,permanent_removal,proxy_account_id,public_key,"
                + "realm,receiver_sig_required,shard,stake_period_start,staked_account_id,staked_node_id,"
                + "timestamp_range,type";

        var nullable = "alias,auto_renew_account_id,auto_renew_period,balance,balance_timestamp,created_timestamp,"
                + "deleted,ethereum_nonce,evm_address,expiration_timestamp,key,max_automatic_token_associations,"
                + "obtainer_id,permanent_removal,proxy_account_id,public_key,receiver_sig_required,stake_period_start,"
                + "staked_account_id,staked_node_id";
        var updatable = "auto_renew_account_id,auto_renew_period,balance,balance_timestamp,decline_reward,deleted,"
                + "ethereum_nonce,expiration_timestamp,key,max_automatic_token_associations,memo,obtainer_id,"
                + "permanent_removal,proxy_account_id,public_key,receiver_sig_required,stake_period_start,"
                + "staked_account_id,staked_node_id,timestamp_range,type";

        var entity = domainBuilder.entity().get();
        var newValue = new byte[] {0, 1, 2};
        var metadata = registry.lookup(Entity.class);

        assertThat(metadata)
                .isNotNull()
                .returns("entity", EntityMetadata::getTableName)
                .returns(true, e -> e.getUpsertable().history())
                .returns("id", e -> e.columns(ColumnMetadata::isId, "{0}"))
                .returns(all, e -> e.columns("{0}"))
                .returns(nullable, e -> e.columns(ColumnMetadata::isNullable, "{0}"))
                .returns(updatable, e -> e.columns(ColumnMetadata::isUpdatable, "{0}"))
                .extracting(EntityMetadata::getColumns, InstanceOfAssertFactories.ITERABLE)
                .hasSize(28)
                .first(InstanceOfAssertFactories.type(ColumnMetadata.class))
                .returns("alias", ColumnMetadata::getName)
                .returns(byte[].class, ColumnMetadata::getType)
                .returns(false, ColumnMetadata::isId)
                .returns(true, ColumnMetadata::isNullable)
                .returns(null, ColumnMetadata::getDefaultValue)
                .returns(false, ColumnMetadata::isUpdatable)
                .returns(null, ColumnMetadata::getUpsertColumn)
                .satisfies(cm -> assertThat(cm.getGetter().apply(entity)).isEqualTo(entity.getAlias()))
                .satisfies(cm -> assertThatCode(() -> cm.getSetter().accept(entity, newValue))
                        .satisfies(d -> assertThat(entity.getAlias()).isEqualTo(newValue)));
    }

    @Test
    @Transactional
    void lookupSameColumnNameFromMultipleDomainClasses() {
        // The test case reproduces the issue in 4265 With the entity manager cache, if we look up two domain classes
        // metadata in the same session, for columns with the same name, the defaults of the first domain class will
        // override those of the second. For example, without the fix, entity.type's default will be "'FUNGIBLE_COMMON'"
        // given, when
        var tokenMetadata = registry.lookup(Token.class);
        var entityMetadata = registry.lookup(Entity.class);

        // then
        var typeDefaultValues = Stream.of(entityMetadata, tokenMetadata)
                .flatMap(m -> m.getColumns().stream())
                .filter(c -> "type".equals(c.getName()))
                .map(ColumnMetadata::getDefaultValue)
                .toList();
        assertThat(typeDefaultValues).containsExactly("'UNKNOWN'", "'FUNGIBLE_COMMON'");
    }

    @Test
    void nonExisting() {
        assertThatThrownBy(() -> registry.lookup(NonExisting.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notUpsertable() {
        assertThatThrownBy(() -> registry.lookup(Object.class)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void notEntity() {
        assertThatThrownBy(() -> registry.lookup(NonEntity.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertColumn() {
        var metadata = registry.lookup(Token.class);
        assertThat(metadata)
                .isNotNull()
                .returns("token_id", e -> e.columns(ColumnMetadata::isId, "{0}"))
                .extracting(e -> e.getColumns().stream()
                        .filter(c -> c.getName().equals("total_supply"))
                        .findFirst()
                        .get())
                .extracting(ColumnMetadata::getUpsertColumn)
                .isNotNull()
                .extracting(UpsertColumn::coalesce)
                .isNotNull();
    }

    @Test
    void upsertColumnNoCoalesce() {
        var metadata = registry.lookup(CustomFee.class);
        assertThat(metadata)
                .isNotNull()
                .returns(
                        "fixed_fees",
                        m -> m.column(c -> Objects.equals(c.getName(), "fixed_fees"), "coalesce(e_{0}, {0})"))
                .returns("e_fixed_fees", m -> m.column(c -> Objects.equals(c.getName(), "fixed_fees"), "e_{0}"));
    }

    @Upsertable
    private static class NonEntity {}

    @Data
    @jakarta.persistence.Entity
    @Upsertable
    private static class NonExisting implements Persistable<Integer> {
        @Id
        private Integer id;

        @Override
        public boolean isNew() {
            return true;
        }
    }
}
