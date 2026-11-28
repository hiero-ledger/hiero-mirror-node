// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;

import com.google.common.base.CaseFormat;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

@RequiredArgsConstructor
class CompositeIdPersistenceTest extends ImporterIntegrationTest {

    private final BatchPersister batchPersister;
    private final JdbcAggregateOperations entityOperations;
    private final RelationalMappingContext mappingContext;

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    void copyAndReadCompositeId(String table, Object expected, String keyColumns) {
        batchPersister.persist(List.of(expected));

        // Inspect the actual COPY columns independently of Spring's ID metadata.
        var row = jdbcOperations.queryForMap("select " + keyColumns + " from " + table);
        var bean = new BeanWrapperImpl(expected);
        row.forEach((column, actual) -> {
            var property = expected instanceof SidecarFile && column.equals("id")
                    ? "index"
                    : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, column);
            var value = bean.getPropertyValue(property);
            if (value instanceof EntityId entityId) {
                value = entityId.getId();
            }
            assertThat(actual).as("%s.%s", table, column).isEqualTo(value);
        });

        var type = expected.getClass();
        var entity = mappingContext.getRequiredPersistentEntity(type);
        var id = entity.getIdentifierAccessor(expected).getRequiredIdentifier();
        assertThat(entityOperations.findById(id, type)).isEqualTo(expected);

        if (AnnotationUtils.findAnnotation(type, Upsertable.class) != null) {
            batchPersister.persist(List.of(expected));
            assertThat(entityOperations.count(type)).isEqualTo(1L);
            assertThat(entityOperations.findById(id, type)).isEqualTo(expected);
        }
    }

    private static Stream<Arguments> entities() {
        var builder = new DomainBuilder();
        return Stream.of(
                Arguments.of(
                        "address_book_service_endpoint",
                        builder.addressBookServiceEndpoint().get(),
                        "consensus_timestamp,domain_name,ip_address_v4,node_id,port"),
                Arguments.of(
                        "node_stake",
                        builder.nodeStake().customize(b -> b.nodeId(0)).get(),
                        "consensus_timestamp,node_id"),
                Arguments.of("contract_action", builder.contractAction().get(), "consensus_timestamp,index"),
                Arguments.of("contract_log", builder.contractLog().get(), "consensus_timestamp,index"),
                Arguments.of("contract_state", builder.contractState().get(), "contract_id,slot"),
                Arguments.of(
                        "contract_state_change",
                        builder.contractStateChange().get(),
                        "consensus_timestamp,contract_id,slot"),
                Arguments.of(
                        "contract_transaction", builder.contractTransaction().get(), "consensus_timestamp,entity_id"),
                Arguments.of("crypto_allowance", builder.cryptoAllowance().get(), "owner,spender"),
                Arguments.of("nft_allowance", builder.nftAllowance().get(), "owner,spender,token_id"),
                Arguments.of("token_allowance", builder.tokenAllowance().get(), "owner,spender,token_id"),
                Arguments.of("entity_transaction", builder.entityTransaction().get(), "consensus_timestamp,entity_id"),
                Arguments.of("hook", builder.hook().get(), "hook_id,owner_id"),
                Arguments.of("hook_storage", builder.hookStorage().get(), "hook_id,key,owner_id"),
                Arguments.of(
                        "hook_storage_change",
                        builder.hookStorageChange().get(),
                        "consensus_timestamp,hook_id,key,owner_id"),
                Arguments.of("nft", builder.nft().get(), "serial_number,token_id"),
                Arguments.of("token_account", builder.tokenAccount().get(), "account_id,token_id"),
                Arguments.of(
                        "token_airdrop",
                        builder.tokenAirdrop(FUNGIBLE_COMMON).get(),
                        "receiver_account_id,sender_account_id,serial_number,token_id"),
                Arguments.of(
                        "token_airdrop",
                        builder.tokenAirdrop(NON_FUNGIBLE_UNIQUE).get(),
                        "receiver_account_id,sender_account_id,serial_number,token_id"),
                Arguments.of(
                        "topic_message_lookup", builder.topicMessageLookup().get(), "partition,topic_id"),
                Arguments.of(
                        "assessed_custom_fee",
                        builder.assessedCustomFee().get(),
                        "collector_account_id,consensus_timestamp"),
                Arguments.of("crypto_transfer", builder.cryptoTransfer().get(), "amount,consensus_timestamp,entity_id"),
                Arguments.of("sidecar_file", builder.sidecarFile().get(), "consensus_end,id"),
                Arguments.of(
                        "staking_reward_transfer",
                        builder.stakingRewardTransfer().get(),
                        "account_id,consensus_timestamp"),
                Arguments.of(
                        "transaction_signature",
                        builder.transactionSignature().get(),
                        "consensus_timestamp,public_key_prefix"));
    }
}
