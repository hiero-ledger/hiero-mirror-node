// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.UNKNOWN;
import static org.hiero.mirror.importer.util.UtilityTest.ALIAS_ECDSA_SECP256K1;
import static org.hiero.mirror.importer.util.UtilityTest.EVM_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@RequiredArgsConstructor
@ExtendWith(OutputCaptureExtension.class)
class EntityIdServiceImplTest extends ImporterIntegrationTest {

    private static final int EVM_ADDRESS_NUM = 100;
    private static final byte[] PARSABLE_EVM_ADDRESS = new byte[] {
        0,
        0,
        0,
        0, // shard
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0, // realm
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        EVM_ADDRESS_NUM, // num
    };

    private static final String RECOVERABLE_ERROR_LOG_PREFIX = "Recoverable error. ";

    private final EntityRepository entityRepository;
    private final EntityIdService entityIdService;

    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 0L), Arguments.of(0L, 1L), Arguments.of(1L, 2L));
    }

    @Test
    void cache() {
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.alias(null).type(CONTRACT))
                .persist();
        ContractID contractId = getProtoContractId(contract);
        EntityId expected = contract.toEntityId();

        // db query and cache put
        assertThat(entityIdService.lookup(contractId)).hasValue(expected);

        // mark it as deleted
        contract.setDeleted(true);
        entityRepository.save(contract);

        // cache hit
        assertThat(entityIdService.lookup(contractId)).hasValue(expected);

        entityRepository.deleteById(contract.getId());
        assertThat(entityIdService.lookup(contractId)).hasValue(expected);

        // cache miss
        reset();
        var contractIdProto = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractIdProto)).isEmpty();
    }

    @Test
    void lookupAccountNum() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        assertThat(entityIdService.lookup(accountId)).hasValue(EntityId.of(100));
    }

    @MethodSource("shardAndRealmData")
    @ParameterizedTest
    void lookupAccountAlias(long shard, long realm) {
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
        var account = domainBuilder.entity().persist();
        assertThat(entityIdService.lookup(getProtoAccountId(account))).hasValue(account.toEntityId());
    }

    @Test
    void lookupAccountAliasNoMatch() {
        Entity account = domainBuilder.entity().get();
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isEmpty();
    }

    @Test
    void lookupAccountAliasDeleted() {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isEmpty();
    }

    @Test
    void lookupAccountDefaultInstance() {
        assertThat(entityIdService.lookup(AccountID.getDefaultInstance())).hasValue(EntityId.EMPTY);
    }

    @Test
    void lookupAccountNull() {
        assertThat(entityIdService.lookup((AccountID) null)).hasValue(EntityId.EMPTY);
    }

    @Test
    void lookupAccountNotFound() {
        AccountID accountId = AccountID.newBuilder().setRealmNum(1).build();
        assertThat(entityIdService.lookup(accountId)).isEmpty();
    }

    @Test
    void lookupAccounts() {
        AccountID nullAccountId = null;
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        AccountID accountIdInvalid = AccountID.newBuilder().setRealmNum(1).build();
        Entity accountDeleted =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        var entityId = entityIdService.lookup(
                nullAccountId,
                AccountID.getDefaultInstance(),
                getProtoAccountId(accountDeleted),
                accountIdInvalid,
                accountId);

        assertThat(entityId).hasValue(EntityId.of(accountId));
    }

    @Test
    void lookupAccountAliasToEvmAddressFromCache() {
        // given
        var entity =
                domainBuilder.entity().customize(e -> e.evmAddress(EVM_ADDRESS)).get();
        var accountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1))
                .build();

        // when
        entityIdService.notify(entity);

        // then
        assertThat(entityIdService.lookup(accountId)).hasValue(entity.toEntityId());
    }

    @Test
    void lookupAccountAliasToEvmAddressFromDb() {
        // given
        var entity =
                domainBuilder.entity().customize(e -> e.evmAddress(EVM_ADDRESS)).persist();
        var accountId = AccountID.newBuilder()
                .setShardNum(COMMON_PROPERTIES.getShard())
                .setRealmNum(COMMON_PROPERTIES.getRealm())
                .setAlias(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1))
                .build();
        // when, then
        assertThat(entityIdService.lookup(accountId)).hasValue(entity.toEntityId());
    }

    @Test
    void lookupAccountAliasToEvmAddressNotFound() {
        var accountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1))
                .build();
        assertThat(entityIdService.lookup(accountId)).isEmpty();
    }

    @Test
    void lookupAccountsReturnsFirst() {
        AccountID accountId1 = AccountID.newBuilder().setAccountNum(100).build();
        AccountID accountId2 = AccountID.newBuilder().setAccountNum(101).build();
        var entityId = entityIdService.lookup(accountId1, accountId2);
        assertThat(entityId).hasValue(EntityId.of(accountId1));
    }

    @Test
    void lookupAccountsEntityIdNotFound() {
        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().customize(e -> e.alias(null)).get();
        var accountId1 = getProtoContractId(account1);
        var accountId2 = getProtoContractId(account2);
        assertThat(entityIdService.lookup(accountId1, accountId2)).hasValue(EntityId.EMPTY);
    }

    @Test
    void lookupContractNum() {
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        assertThat(entityIdService.lookup(contractId)).hasValue(EntityId.of(100));
    }

    @Test
    void lookupContractNumInvalid() {
        var contractId =
                ContractID.newBuilder().setContractNum(1514739994982350848L).build();
        assertThat(entityIdService.lookup(contractId)).isEmpty();
    }

    @MethodSource("shardAndRealmData")
    @ParameterizedTest
    void lookupContractEvmAddress(long shard, long realm) {
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
        var contract = domainBuilder
                .entity()
                .customize(e -> e.alias(null).type(CONTRACT))
                .persist();
        assertThat(entityIdService.lookup(getProtoContractId(contract))).hasValue(contract.toEntityId());
    }

    @Test
    void lookupContractEvmAddressSpecific() {
        var contractId = ContractID.newBuilder()
                .setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();
        assertThat(entityIdService.lookup(contractId))
                .hasValue(EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), EVM_ADDRESS_NUM));
    }

    @Test
    void lookupContractEvmAddressNoMatch(CapturedOutput output) {
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.alias(null).type(CONTRACT))
                .get();
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isEmpty();
        assertThat(output.getAll()).containsIgnoringCase(RECOVERABLE_ERROR_LOG_PREFIX);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lookupContractEvmAddressRecoverableError(boolean throwRecoverableError, CapturedOutput output) {
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.alias(null).type(CONTRACT))
                .get();
        var contractId = getProtoContractId(contract);

        assertThat(entityIdService.lookup(contractId, throwRecoverableError)).isEmpty();
        if (throwRecoverableError) {
            assertThat(output.getAll()).containsIgnoringCase(RECOVERABLE_ERROR_LOG_PREFIX);
        } else {
            assertThat(output.getAll()).doesNotContainIgnoringCase(RECOVERABLE_ERROR_LOG_PREFIX);
        }
    }

    @Test
    void lookupContractEvmAddressDeleted() {
        Entity contract = domainBuilder
                .entity()
                .customize(e -> e.alias(null).deleted(true).type(CONTRACT))
                .persist();
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isEmpty();
    }

    @Test
    void lookupContractEvmAddressShardRealmMismatch() {
        var contractId = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();
        var expected = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), EVM_ADDRESS_NUM);
        assertThat(entityIdService.lookup(contractId).get()).isEqualTo(expected);
    }

    @Test
    void lookupContractDefaultInstance() {
        assertThat(entityIdService.lookup(ContractID.getDefaultInstance())).hasValue(EntityId.EMPTY);
    }

    @Test
    void lookupContractNull() {
        assertThat(entityIdService.lookup((ContractID) null)).hasValue(EntityId.EMPTY);
    }

    @Test
    void lookupContracts() {
        ContractID nullContractId = null;
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        ContractID contractIdInvalid = ContractID.newBuilder().setRealmNum(1).build();
        Entity contractDeleted = domainBuilder
                .entity()
                .customize(e -> e.alias(null).deleted(true).type(CONTRACT))
                .persist();

        var entityId = entityIdService.lookup(
                nullContractId,
                ContractID.getDefaultInstance(),
                getProtoContractId(contractDeleted),
                contractIdInvalid,
                contractId);

        assertThat(entityId).hasValue(EntityId.of(contractId));
    }

    @Test
    void lookupContractsReturnsFirst() {
        ContractID contractId1 = ContractID.newBuilder().setContractNum(100).build();
        ContractID contractId2 = ContractID.newBuilder().setContractNum(101).build();
        var entityId = entityIdService.lookup(contractId1, contractId2);
        assertThat(entityId).hasValue(EntityId.of(contractId1));
    }

    @Test
    void lookupContractsEntityIdNotFound() {
        Entity contract1 = domainBuilder
                .entity()
                .customize(e -> e.alias(null).type(CONTRACT))
                .get();
        Entity contract2 = domainBuilder
                .entity()
                .customize(c -> c.alias(null).evmAddress(null).type(CONTRACT))
                .get();
        var contractId1 = getProtoContractId(contract1);
        var contractId2 = getProtoContractId(contract2);
        assertThat(entityIdService.lookup(contractId1, contractId2)).hasValue(contract2.toEntityId());
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {"false", "null"})
    void notifyAccount(Boolean deleted) {
        Entity account =
                domainBuilder.entity().customize(e -> e.deleted(deleted)).get();
        var alias = getProtoAccountId(account);
        var evmAddress = alias.toBuilder()
                .setAlias(DomainUtils.fromBytes(account.getEvmAddress()))
                .build();
        entityIdService.notify(account);
        assertThat(entityIdService.lookup(alias)).hasValue(account.toEntityId());
        assertThat(entityIdService.lookup(evmAddress)).hasValue(account.toEntityId());
    }

    @Test
    void notifyAccountDeleted() {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(true)).get();
        entityIdService.notify(account);
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"false", ","})
    void notifyContract(Boolean deleted) {
        Entity contract = domainBuilder
                .entity()
                .customize(c -> c.alias(null).deleted(deleted).type(CONTRACT))
                .get();
        entityIdService.notify(contract);
        assertThat(entityIdService.lookup(getProtoContractId(contract))).hasValue(contract.toEntityId());
    }

    @Test
    void notifyContractDeleted() {
        Entity contract = domainBuilder
                .entity()
                .customize(c -> c.alias(null).deleted(true).type(CONTRACT))
                .get();
        entityIdService.notify(contract);
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isEmpty();
    }

    @Test
    void notifyNull() {
        assertDoesNotThrow(() -> entityIdService.notify(null));
    }

    @Test
    void notifyUnknown() {
        Entity contract = domainBuilder
                .entity()
                .customize(
                        c -> c.alias(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS).toByteArray())
                                .type(UNKNOWN))
                .get();
        entityIdService.notify(contract);
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isEmpty();
    }

    @Test
    void lookupAccountEvmAddress() {
        AccountID accountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();

        assertThat(entityIdService.lookup(accountId))
                .hasValue(EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), EVM_ADDRESS_NUM));
    }

    @Test
    void lookupAccountEvmAddressNotFound() {
        AccountID accountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(domainBuilder.evmAddress()))
                .build();
        assertThat(entityIdService.lookup(accountId)).isNotPresent();
    }

    private AccountID getProtoAccountId(Entity account) {
        var accountId = AccountID.newBuilder().setShardNum(account.getShard()).setRealmNum(account.getRealm());
        if (account.getAlias() == null) {
            accountId.setAccountNum(account.getNum());
        } else {
            accountId.setAlias(DomainUtils.fromBytes(account.getAlias()));
        }
        return accountId.build();
    }

    private ContractID getProtoContractId(Entity contract) {
        var contractId =
                ContractID.newBuilder().setShardNum(contract.getShard()).setRealmNum(contract.getRealm());
        if (contract.getEvmAddress() == null) {
            contractId.setContractNum(contract.getNum());
        } else {
            contractId.setEvmAddress(DomainUtils.fromBytes(contract.getEvmAddress()));
        }
        return contractId.build();
    }
}
