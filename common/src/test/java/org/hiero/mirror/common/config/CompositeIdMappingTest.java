// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.CaseFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hiero.mirror.common.converter.EntityIdToLongConverter;
import org.hiero.mirror.common.converter.LongToEntityIdConverter;
import org.hiero.mirror.common.converter.PGobjectToRangeReadingConverter;
import org.hiero.mirror.common.converter.RangeToPGobjectWritingConverter;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowanceHistory;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowanceHistory;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookHistory;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropHistory;
import org.hiero.mirror.common.domain.topic.TopicMessageLookup;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.relational.core.conversion.MappingRelationalConverter;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.domain.RowDocument;

class CompositeIdMappingTest {

    private final RelationalMappingContext mappingContext = new RelationalMappingContext();
    private final MappingRelationalConverter converter;

    CompositeIdMappingTest() {
        var conversions = new JdbcCustomConversions(List.of(
                new EntityIdToLongConverter(),
                new LongToEntityIdConverter(),
                new PGobjectToRangeReadingConverter(),
                new RangeToPGobjectWritingConverter()));
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        converter = new MappingRelationalConverter(mappingContext, conversions);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("compositeIds")
    void hydrateCompositeId(Class<?> type, Map<String, Object> columns) {
        assertHydratedKeys(type, columns);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("compositeIds")
    void hydrateZeroIds(Class<?> type, Map<String, Object> columns) {
        var zeros = new HashMap<>(columns);
        zeros.replaceAll((name, value) -> {
            if (value instanceof Integer) {
                return 0;
            }
            return value instanceof Long ? 0L : value;
        });
        assertHydratedKeys(type, zeros);
    }

    private void assertHydratedKeys(Class<?> type, Map<String, Object> columns) {
        var document = new RowDocument();
        document.putAll(columns);
        var bean = new BeanWrapperImpl(converter.read(type, document));
        var entity = mappingContext.getRequiredPersistentEntity(type);
        assertThat(entity.getRequiredIdProperty().isEmbedded()).isTrue();
        assertThat(entity.getIdentifierAccessor(bean.getWrappedInstance()).getIdentifier())
                .isNotNull();

        columns.forEach((column, expected) -> {
            var name = type == SidecarFile.class && column.equals("id")
                    ? "index"
                    : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, column);
            var actual = bean.getPropertyValue(name);
            if (actual instanceof EntityId entityId) {
                actual = entityId.getId();
            }
            assertThat(actual)
                    .as("%s.%s from %s", type.getSimpleName(), name, column)
                    .isEqualTo(expected);
        });
    }

    private static Stream<Arguments> compositeIds() {
        return Stream.of(
                Arguments.of(
                        AddressBookServiceEndpoint.class,
                        Map.of(
                                "consensus_timestamp",
                                101L,
                                "domain_name",
                                "node.example",
                                "ip_address_v4",
                                "127.0.0.1",
                                "node_id",
                                7L,
                                "port",
                                50211)),
                Arguments.of(NodeStake.class, Map.of("consensus_timestamp", 101L, "node_id", 7L)),
                Arguments.of(
                        ClprConnector.class,
                        Map.of("channel_id", new byte[] {0, 1, 2}, "commitment", new byte[] {3, 4})),
                Arguments.of(ClprMessage.class, Map.of("channel_id", new byte[] {0, 1, 2}, "message_id", 101L)),
                Arguments.of(ContractAction.class, Map.of("consensus_timestamp", 101L, "index", 3)),
                Arguments.of(ContractLog.class, Map.of("consensus_timestamp", 101L, "index", 3)),
                Arguments.of(ContractState.class, Map.of("contract_id", 102L, "slot", new byte[] {0, 1, 2})),
                Arguments.of(
                        ContractStateChange.class,
                        Map.of("consensus_timestamp", 101L, "contract_id", 102L, "slot", new byte[] {0, 1, 2})),
                Arguments.of(ContractTransaction.class, Map.of("consensus_timestamp", 101L, "entity_id", 102L)),
                Arguments.of(CryptoAllowance.class, Map.of("owner", 101L, "spender", 102L)),
                Arguments.of(NftAllowance.class, Map.of("owner", 101L, "spender", 102L, "token_id", 103L)),
                Arguments.of(TokenAllowance.class, Map.of("owner", 101L, "spender", 102L, "token_id", 103L)),
                Arguments.of(EntityTransaction.class, Map.of("consensus_timestamp", 101L, "entity_id", 102L)),
                Arguments.of(Hook.class, Map.of("hook_id", -7L, "owner_id", 102L)),
                Arguments.of(HookStorage.class, Map.of("hook_id", -7L, "key", new byte[] {0, 1, 2}, "owner_id", 102L)),
                Arguments.of(
                        HookStorageChange.class,
                        Map.of(
                                "consensus_timestamp",
                                101L,
                                "hook_id",
                                -7L,
                                "key",
                                new byte[] {0, 1, 2},
                                "owner_id",
                                102L)),
                Arguments.of(Nft.class, Map.of("serial_number", 101L, "token_id", 102L)),
                Arguments.of(TokenAccount.class, Map.of("account_id", 101L, "token_id", 102L)),
                Arguments.of(
                        TokenAirdrop.class,
                        Map.of(
                                "receiver_account_id",
                                101L,
                                "sender_account_id",
                                102L,
                                "serial_number",
                                103L,
                                "token_id",
                                104L)),
                Arguments.of(TopicMessageLookup.class, Map.of("partition", "topic_message_01", "topic_id", 101L)),
                Arguments.of(
                        AssessedCustomFee.class, Map.of("collector_account_id", 101L, "consensus_timestamp", 102L)),
                Arguments.of(
                        CryptoTransfer.class, Map.of("amount", -7L, "consensus_timestamp", 101L, "entity_id", 102L)),
                Arguments.of(SidecarFile.class, Map.of("consensus_end", 101L, "id", 3)),
                Arguments.of(StakingRewardTransfer.class, Map.of("account_id", 101L, "consensus_timestamp", 102L)),
                Arguments.of(
                        TransactionSignature.class,
                        Map.of("consensus_timestamp", 101L, "public_key_prefix", new byte[] {0, 1, 2})),
                Arguments.of(
                        ClprConnectorHistory.class,
                        Map.of("channel_id", new byte[] {0, 1, 2}, "commitment", new byte[] {3, 4})),
                Arguments.of(CryptoAllowanceHistory.class, Map.of("owner", 101L, "spender", 102L)),
                Arguments.of(NftAllowanceHistory.class, Map.of("owner", 101L, "spender", 102L, "token_id", 103L)),
                Arguments.of(TokenAllowanceHistory.class, Map.of("owner", 101L, "spender", 102L, "token_id", 103L)),
                Arguments.of(HookHistory.class, Map.of("hook_id", -7L, "owner_id", 102L)),
                Arguments.of(NftHistory.class, Map.of("serial_number", 101L, "token_id", 102L)),
                Arguments.of(TokenAccountHistory.class, Map.of("account_id", 101L, "token_id", 102L)),
                Arguments.of(
                        TokenAirdropHistory.class,
                        Map.of(
                                "receiver_account_id",
                                101L,
                                "sender_account_id",
                                102L,
                                "serial_number",
                                103L,
                                "token_id",
                                104L)));
    }
}
