// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.utils.EvmTokenUtils;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.state.State;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

@Import(Web3jTestConfiguration.class)
public abstract class AbstractContractCallServiceTest extends Web3IntegrationTest {

    protected static final String TREASURY_ADDRESS = EvmTokenUtils.toAddress(2).toHexString();
    protected static final long DEFAULT_ACCOUNT_BALANCE = 100_000_000_000_000_000L;
    protected static final long DEFAULT_TOKEN_BALANCE = 100;
    protected static final BigInteger DEFAULT_SERIAL_NUMBER = BigInteger.ONE;
    protected static final List<BigInteger> DEFAULT_SERIAL_NUMBERS_LIST = List.of(DEFAULT_SERIAL_NUMBER);
    protected static final BigInteger INVALID_SERIAL_NUMBER = BigInteger.valueOf(Long.MAX_VALUE);
    protected static final int DEFAULT_DECIMALS = 12;
    protected static final long DEFAULT_TOKEN_SUPPLY = 1000L;
    protected static final long DEFAULT_AMOUNT_GRANTED = 10L;
    protected static final BigInteger DEFAULT_FEE_AMOUNT = BigInteger.valueOf(100L);
    protected static final BigInteger DEFAULT_DENOMINATOR_VALUE = BigInteger.valueOf(10L);
    protected static final BigInteger DEFAULT_NUMERATOR_VALUE = BigInteger.valueOf(20L);
    protected static final BigInteger DEFAULT_FEE_MIN_VALUE = BigInteger.valueOf(1L);
    protected static final BigInteger DEFAULT_FEE_MAX_VALUE = BigInteger.valueOf(1000L);

    @Resource
    protected TestWeb3jService testWeb3jService;

    @Resource
    protected MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Resource
    protected State state;

    @Resource
    protected ContractExecutionService contractExecutionService;

    protected RecordFile genesisRecordFile;
    protected Entity treasuryEntity;
    protected double modularizedTrafficPercent;

    public static Key getKeyWithDelegatableContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setDelegatableContractId(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    public static Key getKeyWithContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setContractID(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    @BeforeEach
    protected void setup() {
        modularizedTrafficPercent = mirrorNodeEvmProperties.getModularizedTrafficPercent();
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(1.0);
        }

        genesisRecordFile =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        treasuryEntity = domainBuilder
                .entity()
                .customize(e -> e.id(2L)
                        .num(2L)
                        // The balance should not be set to max value 5000000000000000000L, because if we use it as a
                        // node operator it would not be able to receive rewards and can cause failures
                        .balance(1000000000000000000L)
                        .createdTimestamp(genesisRecordFile.getConsensusStart())
                        .timestampRange(Range.atLeast(genesisRecordFile.getConsensusStart())))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(98L)
                        .num(98L)
                        .createdTimestamp(genesisRecordFile.getConsensusStart())
                        .timestampRange(Range.atLeast(genesisRecordFile.getConsensusStart())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(
                                treasuryEntity.getCreatedTimestamp(), treasuryEntity.toEntityId()))
                        .balance(treasuryEntity.getBalance()))
                .persist();
    }

    @AfterEach
    void cleanup() {
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(modularizedTrafficPercent);
        }

        testWeb3jService.reset();
    }

    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        try {
            return contractExecutionService.callContract(serviceParameters).getGasUsed();
        } catch (MirrorEvmTransactionException e) {
            // Some tests expect to fail but still want to capture the gas used
            if (mirrorNodeEvmProperties.isModularizedServices()) {
                return e.getResult().getGasUsed();
            } else {
                var result = e.getResult();
                if (result != null) {
                    return result.getGasUsed();
                }
            }
            throw e;
        }
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify eth_call
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify eth_estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected <T extends Exception> void verifyEstimateGasRevertExecution(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final String exceptionMessage,
            Class<T> exceptionClass) {

        testWeb3jService.setEstimateGas(true);
        // Verify estimate reverts with proper message
        assertThatThrownBy(functionCall::send).isInstanceOf(exceptionClass).hasMessage(exceptionMessage);
    }

    protected void verifyEthCallAndEstimateGasWithValue(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(
                functionCall, contract, payerAddress, value, mirrorNodeEvmProperties.isModularizedServices()));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify ethCall
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract) {
        return getContractExecutionParameters(
                functionCall, contract, Address.ZERO, 0L, mirrorNodeEvmProperties.isModularizedServices());
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiver, final Address payerAddress, final long value) {
        return getContractExecutionParameters(
                data, receiver, payerAddress, value, ETH_CALL, mirrorNodeEvmProperties.isModularizedServices());
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final Bytes data,
            final Address receiverAddress,
            final Address senderAddress,
            final long value,
            final CallType callType,
            final boolean isModularized) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(callType == ETH_ESTIMATE_GAS)
                .isModularized(isModularized)
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(senderAddress))
                .value(value)
                .build();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value,
            final boolean isModularized) {
        return getContractExecutionParameters(
                Bytes.fromHexString(functionCall.encodeFunctionCall()),
                Address.fromHexString(contract.getContractAddress()),
                payerAddress,
                value);
    }

    /**
     * Persists entity of type token in the entity db table. Entity table contains properties common for all entities on
     * the network (tokens, accounts, smart contracts, topics)
     */
    protected Entity tokenEntityPersist() {
        return domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();
    }

    /**
     * Method used to persist  Token with no specific customization
     * @return Token object that is persisted in db
     */
    protected Token fungibleTokenPersist() {
        return fungibleTokenCustomizable(t -> {});
    }

    /**
     * Method used to persist Token with treasury account as specific customization
     *
     * @param treasuryEntityId - the treasuryEntityId that has to be set in the token
     * @return Token object that is persisted in db
     */
    protected Token fungibleTokenPersistWithTreasuryAccount(final EntityId treasuryEntityId) {
        return fungibleTokenCustomizable(t -> t.treasuryAccountId(treasuryEntityId));
    }

    protected FixedFee fixedFeePersist(Token token, Entity collectorAccount, Long amount) {
        final var fixedFee = FixedFee.builder()
                .amount(amount)
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(EntityId.of(token.getTokenId()))
                .build();

        domainBuilder
                .customFee()
                .customize(f -> f.entityId(token.getTokenId())
                        .fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of()))
                .persist();
        return fixedFee;
    }

    /**
     * Method used to persist Token with token entity id and additional customization
     * provided in the customizer
     *
     * @param customizer - the consumer used to customize the token
     * @return Token object which is persisted in the database
     */
    protected Token fungibleTokenCustomizable(final Consumer<Token.TokenBuilder<?, ?>> customizer) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(EntityType.TOKEN)).persist();

        return domainBuilder
                .token()
                .customize(t -> {
                    t.tokenId(tokenEntity.getId()).type(TokenTypeEnum.FUNGIBLE_COMMON);
                    customizer.accept(t); // Apply any customizations provided
                })
                .persist();
    }

    /**
     * Creates fungible token in the token db table.
     * The token table stores the properties specific for tokens and each record refers to
     * another one in the entity table, which has the properties common for all entities.
     *
     * @param tokenEntity     The entity from the entity db table related to the created token table record
     * @param treasuryAccount The account holding the initial token supply
     */
    protected Token fungibleTokenPersist(final Entity tokenEntity, final Entity treasuryAccount) {
        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasuryAccount.toEntityId()))
                .persist();
    }

    /**
     * Persists non-fungible token in the token db table.
     *
     * @param tokenEntity The entity from the entity db table related to the token
     * @param treasuryAccount - The treasury account to be set in the token
     */
    protected Token nonFungibleTokenPersist(final Entity tokenEntity, final Entity treasuryAccount) {
        return domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasuryAccount.toEntityId()))
                .persist();
    }

    /**
     * Persists a non-fungible token entity.
     * @return Token object persisted in the database.
     */
    protected Token nonFungibleTokenPersist() {
        return nonFungibleTokenCustomizable(t -> {});
    }

    /**
     * Persists a non-fungible token with a specific treasury account.
     *
     * @param treasuryEntityId The treasury account ID.
     * @return Token object persisted in the database.
     */
    protected Token nonFungibleTokenPersistWithTreasury(final EntityId treasuryEntityId) {
        return nonFungibleTokenCustomizable(t -> t.treasuryAccountId(treasuryEntityId));
    }

    /**
     * Method used to persist non-fungible token with token id and customization
     * provided in the customizer object
     *
     * @param customizer the consumer used to customize the Token
     * @return Token object that is persisted in the database
     */
    protected Token nonFungibleTokenCustomizable(Consumer<Token.TokenBuilder<?, ?>> customizer) {
        final var nft = tokenEntityPersist();

        return domainBuilder
                .token()
                .customize(t -> {
                    t.tokenId(nft.getId()).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
                    customizer.accept(t);
                })
                .persist();
    }

    /**
     * Creates and persists an NFT entity.
     *
     * @param customizer Consumer to customize NFT attributes.
     */
    protected Nft nftPersistCustomizable(final Consumer<Nft.NftBuilder<?, ?>> customizer) {
        return domainBuilder
                .nft()
                .customize(n -> {
                    n.serialNumber(DEFAULT_SERIAL_NUMBER.longValue());
                    customizer.accept(n);
                })
                .persist();
    }

    /**
     * Method used to persist non-fungible Token with treasury account and
     * Nft with account id, token id and spender as specific customization
     *
     * @param treasury the treasury account with which the non-fungible token is persisted
     * @param accountId the account id with which the Nft is persisted
     * @param spender the spender with which the Nft is persisted
     * @return Token object that is persisted in the database
     */
    protected Token nftPersist(final EntityId treasury, final EntityId accountId, final EntityId spender) {
        final var token = nonFungibleTokenPersistWithTreasury(treasury);
        nftPersistCustomizable(
                n -> n.accountId(accountId).tokenId(token.getTokenId()).spender(spender));
        return token;
    }

    /**
     * Method used to persist TokenAllowance object with specific customization
     * provided in the customizer object
     *
     * @param customizer the consumer used to customize the TokenAllowance
     * @return TokenAllowance object that is persisted in the database
     */
    protected TokenAllowance tokenAllowancePersistCustomizable(
            final Consumer<TokenAllowance.TokenAllowanceBuilder<?, ?>> customizer) {
        return domainBuilder.tokenAllowance().customize(customizer).persist();
    }

    /**
     * Method used to persist TokenAllowance object with token id,
     * spender id, DEFAULT_AMOUNT_GRANTED and owner id as specific customizations
     *
     * @param spenderId the spender id with which the TokenAllowance is persisted
     * @param ownerId the owner id with which the TokenAllowance is persisted
     * @param tokenId the token id with which the TokenAllowance is persisted
     */
    protected void tokenAllowancePersist(final long spenderId, final long ownerId, final long tokenId) {
        tokenAllowancePersistCustomizable(ta -> ta.tokenId(tokenId)
                .spender(spenderId)
                .amount(DEFAULT_AMOUNT_GRANTED)
                .owner(ownerId));
    }

    /**
     * Method used to persist NftAllowance with specific customization
     * provided in the customizer object
     *
     * @param customizer the consumer used to customize the NftAllowance
     */
    protected void nftAllowancePersistCustomizable(final Consumer<NftAllowance.NftAllowanceBuilder<?, ?>> customizer) {
        domainBuilder.nftAllowance().customize(customizer).persist();
    }

    /**
     * This method creates nft allowance for all instances of a specific token type (approvedForAll). The allowance
     * allows the spender to transfer NFTs on the owner's behalf.
     *
     * @param tokenId   the NFT tokenId for which the allowance is created
     * @param owner   the account owning the NFT. In this case he is payer as well
     * @param spenderId the account allowed to transfer the NFT on owner's behalf
     */
    protected void nftAllowancePersist(final long tokenId, final long spenderId, final EntityId owner) {
        nftAllowancePersistCustomizable(a -> a.tokenId(tokenId)
                .spender(spenderId)
                .owner(owner.getId())
                .payerAccountId(owner)
                .approvedForAll(true));
    }

    /**
     * Creates entity of type account in the entity db table.
     * The entity table stores the properties common for all type of entities.
     */
    protected Entity accountEntityPersist() {
        return accountEntityPersistCustomizable(
                e -> e.type(EntityType.ACCOUNT).evmAddress(null).alias(null).balance(DEFAULT_ACCOUNT_BALANCE));
    }

    /**
     * Method used to create an Entity of type account
     * with DEFAULT_ACCOUNT_BALANCE
     *
     * @return Entity that is persisted in the database
     */
    protected Entity accountEntityWithEvmAddressPersist() {
        return accountEntityPersistCustomizable(e -> e.type(EntityType.ACCOUNT).balance(DEFAULT_ACCOUNT_BALANCE));
    }

    /**
     * Method used to persist an Entity with customization
     * provided in the customizer
     *
     * @param customizer - the consumer with which to customize the entity
     * @return Entity that is persisted in the database
     */
    protected Entity accountEntityPersistCustomizable(final Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        return domainBuilder.entity().customize(customizer).persist();
    }

    /**
     * Creates association between a token and an account, which is required for the account(with non-empty kycKey) to
     * hold and operate with the token. Otherwise, ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN will be thrown when executing a
     * transaction involving the token that requires the account to have KYC approval.
     */
    protected TokenAccount tokenAccount(final Consumer<TokenAccount.TokenAccountBuilder<?, ?>> consumer) {
        return domainBuilder
                .tokenAccount()
                .customize(ta -> ta.freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .customize(consumer)
                .persist();
    }

    /**
     * Method used to persist a TokenAccount with token id and account id
     * as specific customizations
     *
     * @param tokenId the token id with which to persist the TokenAccount
     * @param accountId the account id with which to persist the TokenAccount
     * @return TokenAccount that is persisted in the database
     */
    protected TokenAccount tokenAccountPersist(final long tokenId, final long accountId) {
        return tokenAccount(ta -> ta.tokenId(tokenId).accountId(accountId));
    }

    /**
     * Creates a non-fungible token instance with a specific serial number(a record in the nft table is persisted). The
     * instance is tied to a specific token in the token db table.
     * ownerId with value null indicates that the nft instance holder is the treasury account
     *
     * @param token           the token entity that the nft instance is linked to by tokenId
     * @param nftSerialNumber the unique serial number of the nft instance
     * @param ownerId         the id of the account currently holding the nft
     * @param spenderId       id of the approved spender of the nft
     */
    protected Nft nonFungibleTokenInstancePersist(
            final Token token, Long nftSerialNumber, final EntityId ownerId, final EntityId spenderId) {
        return domainBuilder
                .nft()
                .customize(n -> n.tokenId(token.getTokenId())
                        .serialNumber(nftSerialNumber)
                        .accountId(ownerId)
                        .spender(spenderId))
                .persist();
    }

    /** This method adds a record to the account_balance table.
     * When an account balance is updated during a consensus event, an account_balance record with the consensus_timestamp,
     * account_id and balance is created.The balance_timestamp for the account entry is updated as well in the entity table.
     *
     * @param account The account that the account_balance record is going to be created for
     * @param timestamp The timestamp indicating the account balance update
     */
    protected void accountBalancePersist(final Entity account, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestamp, account.toEntityId()))
                        .balance(account.getBalance()))
                .persist();
    }

    /**
     * Persists a record in the token_balance db table (consensus_timestamp, account_id, balance, token_id).
     * Each record represents the fungible token balance that an account holds at a given consensus timestamp.
     * No record for the token balance at a particular timestamp may result in INSUFFICIENT_TOKEN_BALANCE exception
     * for a historical query with the same timestamp.
     */
    protected void tokenBalancePersist(final EntityId account, final EntityId token, final long timestamp) {
        domainBuilder
                .tokenBalance()
                .customize(ab ->
                        ab.id(new TokenBalance.Id(timestamp, account, token)).balance(DEFAULT_TOKEN_BALANCE))
                .persist();
    }

    protected Pair<Entity, Entity> persistTokenWithAutoRenewAndTreasuryAccounts(
            final TokenTypeEnum tokenType, final Entity treasuryAccount) {
        final var autoRenewAccount = accountEntityPersist();
        final var tokenToUpdateEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenToUpdateEntity.getId())
                        .type(tokenType)
                        .treasuryAccountId(treasuryAccount.toEntityId()))
                .persist();

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            domainBuilder
                    .nft()
                    .customize(n -> n.accountId(treasuryAccount.toEntityId())
                            .spender(treasuryAccount.toEntityId())
                            .tokenId(tokenToUpdateEntity.getId())
                            .serialNumber(DEFAULT_SERIAL_NUMBER.longValue()))
                    .persist();

            tokenAccount(ta -> ta.tokenId(tokenToUpdateEntity.getId())
                    .accountId(treasuryAccount.toEntityId().getId())
                    .balance(1L));
        } else {
            tokenAccount(ta -> ta.tokenId(tokenToUpdateEntity.getId())
                    .accountId(treasuryAccount.toEntityId().getId()));
        }

        return Pair.of(tokenToUpdateEntity, autoRenewAccount);
    }

    protected String getAddressFromEntity(final Entity entity) {
        return EvmTokenUtils.toAddress(entity.toEntityId()).toHexString();
    }

    protected String getAliasFromEntity(final Entity entity) {
        return getEvmAddressBytesFromEntity(entity).toHexString();
    }

    protected Bytes getEvmAddressBytesFromEntity(final Entity entity) {
        return Bytes.wrap(entity.getEvmAddress());
    }

    protected Address getAliasAddressFromEntity(final Entity entity) {
        return Address.wrap(getEvmAddressBytesFromEntity(entity));
    }

    protected ContractDebugParameters getDebugParameters(
            final ContractFunctionProviderRecord functionProvider, final Bytes callDataBytes) {
        return ContractDebugParameters.builder()
                .block(functionProvider.block())
                .callData(callDataBytes)
                .consensusTimestamp(domainBuilder.timestamp())
                .gas(TRANSACTION_GAS_LIMIT)
                .isModularized(mirrorNodeEvmProperties.isModularizedServices())
                .receiver(functionProvider.contractAddress())
                .sender(new HederaEvmAccount(functionProvider.sender()))
                .value(functionProvider.value())
                .build();
    }

    protected ContractFunctionProviderRecord getContractFunctionProviderWithSender(
            final String contract, final Entity sender) {
        final var contractAddress = Address.fromHexString(contract);
        final var senderAddress = Address.fromHexString(getAliasFromEntity(sender));
        return ContractFunctionProviderRecord.builder()
                .contractAddress(contractAddress)
                .sender(senderAddress)
                .build();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) {
        return getContractExecutionParameters(
                Bytes.fromHexString(functionCall.encodeFunctionCall()),
                Address.fromHexString(contract.getContractAddress()),
                testWeb3jService.getSender(),
                value);
    }

    protected String getAddressFromEntityId(final EntityId entity) {
        return HEX_PREFIX
                + EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }

    protected String getAddressFromEvmAddress(final byte[] evmAddress) {
        return Address.wrap(Bytes.wrap(evmAddress)).toHexString();
    }

    protected void activateModularizedFlagAndInitializeState()
            throws InvocationTargetException, IllegalAccessException {
        mirrorNodeEvmProperties.setModularizedServices(true);

        Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

        postConstructMethod.setAccessible(true);
        postConstructMethod.invoke(state);

        final Map<String, String> propertiesMap = new ConcurrentHashMap<>();
        propertiesMap.put("contracts.maxRefundPercentOfGasLimit", "100");
        propertiesMap.put("contracts.maxGasPerSec", "15000000");
        mirrorNodeEvmProperties.setProperties(propertiesMap);
    }

    protected void deactivateModularizedFlag() {
        mirrorNodeEvmProperties.setModularizedServices(false);
    }

    public enum KeyType {
        ADMIN_KEY(1),
        KYC_KEY(2),
        FREEZE_KEY(4),
        WIPE_KEY(8),
        SUPPLY_KEY(16),
        FEE_SCHEDULE_KEY(32),
        PAUSE_KEY(64);
        final BigInteger keyTypeNumeric;

        KeyType(Integer keyTypeNumeric) {
            this.keyTypeNumeric = BigInteger.valueOf(keyTypeNumeric);
        }

        public BigInteger getKeyTypeNumeric() {
            return keyTypeNumeric;
        }
    }
}
