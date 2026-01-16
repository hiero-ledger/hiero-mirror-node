// SPDX-License-Identifier: Apache-2.0

package persistence

import (
	"context"
	"fmt"
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	tdomain "github.com/hiero-ledger/hiero-mirror-node/rosetta/test/domain"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/utils"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

const (
	accountNum1 = int64(9000) + iota
	accountNum2
	accountNum3
	accountNum4
	accountNum5
	accountNum6
)

const (
	accountDeleteTimestamp         = secondSnapshotTimestamp + 180
	account1CreatedTimestamp       = firstSnapshotTimestamp - 100
	account1UpdatedTimestamp       = secondSnapshotTimestamp + 10
	account2DeletedTimestamp       = account1CreatedTimestamp - 1
	account2CreatedTimestamp       = account2DeletedTimestamp - 9
	consensusTimestamp             = firstSnapshotTimestamp + 200
	firstSnapshotTimestamp   int64 = 1656693000269913000
	initialAccountBalance    int64 = 12345
	secondSnapshotTimestamp        = consensusTimestamp - 20
	thirdSnapshotTimestamp         = secondSnapshotTimestamp + 200

	// account3, account4, and account5 are for GetAccountAlias tests
	account3CreatedTimestamp = consensusTimestamp + 100
	account4CreatedTimestamp = consensusTimestamp + 110
	account5CreatedTimestamp = consensusTimestamp + 120
	account6CreatedTimestamp = consensusTimestamp + 130
)

var (
	cryptoTransferAmounts = []int64{150, -178}
	defaultContext        = context.Background()

	// account3 has ecdsaSecp256k1 alias, account4 has ed25519 alias, account5 has invalid alias
	account3Alias = utils.MustDecode("0x3a2103d9a822b91df7850274273a338c152e7bcfa2036b24cd9e3b29d07efd949b387a")
	account4Alias = utils.MustDecode("0x12205a081255a92b7c262bc2ea3ab7114b8a815345b3cc40f800b2b40914afecc44e")
	account5Alias = randstr.Bytes(48)
	// alias with invalid public key, the Key message is valid, but it's formed from an invalid 16-byte ED25519 pub key
	account6Alias = utils.MustDecode("0x1210815345b3cc40f800b2b40914afecc44e")
)

// run the suite
func TestAccountRepositorySuite(t *testing.T) {
	suite.Run(t, new(accountRepositorySuite))
}

// run the suite
func TestAccountRepositoryNonDefaultShardRealmSuite(t *testing.T) {
	testSuite := accountRepositorySuite{
		realm: 2,
		shard: 1023,
	}
	suite.Run(t, &testSuite)
}

type accountRepositorySuite struct {
	integrationTest
	suite.Suite
	accountId        types.AccountId
	accountIdString  string
	accountAlias     []byte
	account3Alias    []byte
	account4Alias    []byte
	account5Alias    []byte
	account6Alias    []byte
	publicKeyBytes1  []byte
	publicKeyBytes2  []byte
	realm            int64
	shard            int64
	treasuryEntityId domain.EntityId
}

func (s *accountRepositorySuite) getEntityId(num int64) domain.EntityId {
	return MustEncodeEntityId(s.shard, s.realm, num)
}

func (suite *accountRepositorySuite) SetupSuite() {
	suite.accountId = types.NewAccountIdFromEntityId(suite.getEntityId(accountNum1))
	suite.accountIdString = suite.accountId.String()
	suite.publicKeyBytes1 = utils.MustMarshal(utils.Ed25519PublicKey())
	suite.publicKeyBytes2 = utils.MustMarshal(utils.EcdsaSecp256k1PublicKey())
	suite.treasuryEntityId = suite.getEntityId(2)
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()

	accountId1 := suite.getEntityId(accountNum1).EncodedId
	tdomain.NewEntityBuilder(dbClient, accountId1, account1CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Historical(true).
		Key(suite.publicKeyBytes1).
		TimestampRange(account1CreatedTimestamp, account1UpdatedTimestamp).
		Persist()
	tdomain.NewEntityBuilder(dbClient, accountId1, account1CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Key(suite.publicKeyBytes2).
		ModifiedTimestamp(account1UpdatedTimestamp).
		Persist()

	// account balance files, always add an account_balance row for treasury
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, firstSnapshotTimestamp).
		AddAccountBalance(suite.treasuryEntityId.EncodedId, 2_000_000_000).
		AddAccountBalance(accountId1, initialAccountBalance).
		Persist()
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, thirdSnapshotTimestamp).
		AddAccountBalance(suite.treasuryEntityId.EncodedId, 2_000_000_000).
		Persist()

	// crypto transfers happened at <= first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(110).
		EntityId(accountId1).
		Timestamp(firstSnapshotTimestamp - 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(170).
		EntityId(accountId1).
		Timestamp(firstSnapshotTimestamp).
		Persist()

	// crypto transfers happened at > first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[0]).
		EntityId(accountId1).
		Errata(domain.ErrataTypeInsert).
		Timestamp(firstSnapshotTimestamp + 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(12345).
		EntityId(accountId1).
		Errata(domain.ErrataTypeDelete).
		Timestamp(firstSnapshotTimestamp + 2).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[1]).
		EntityId(accountId1).
		Timestamp(firstSnapshotTimestamp + 5).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(-(initialAccountBalance + sum(cryptoTransferAmounts))).
		EntityId(accountId1).
		Timestamp(accountDeleteTimestamp).
		Persist()

	// accounts for GetAccountAlias tests
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum3).EncodedId, account3CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account3Alias).
		Key(suite.account3Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum4).EncodedId, account4CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account4Alias).
		Key(suite.account4Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum5).EncodedId, account5CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account5Alias).
		Key(suite.account5Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum6).EncodedId, account6CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account6Alias).
		Persist()
}

func (suite *accountRepositorySuite) TestGetAccountAlias() {
	tests := []struct {
		encodedId int64
		expected  string
	}{
		{encodedId: accountNum3, expected: fmt.Sprintf("0.0.%d", accountNum3)},
		{encodedId: accountNum4, expected: fmt.Sprintf("0.0.%d", accountNum4)},
	}

	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual.String())
		})
	}
}

func (suite *accountRepositorySuite) TestGetAccountAliasDbConnectionError() {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(accountNum3))
	repo := NewAccountRepository(invalidDbClient, suite.treasuryEntityId)

	// when
	actual, err := repo.GetAccountAlias(defaultContext, accountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountId() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdNumericAccount() {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(accountNum1))
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actual, err := repo.GetAccountId(defaultContext, accountId)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), accountId, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdDbConnectionError() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(invalidDbClient, suite.treasuryEntityId)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	// transfers before or at the snapshot timestamp should not affect balance calculation
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}

	// when
	// query
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(defaultContext, suite.accountId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes2, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAfterSecondSnapshot() {
	// given
	// remove any transfers in db. with the balance info in the second snapshot, this test verifies the account balance
	// is directly read from the snapshot
	truncateTables(domain.CryptoTransfer{})
	balance := initialAccountBalance + sum(cryptoTransferAmounts)
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, secondSnapshotTimestamp).
		AddAccountBalance(suite.treasuryEntityId.EncodedId, 2_000_000_000).
		AddAccountBalance(suite.getEntityId(accountNum1).EncodedId, balance).
		Persist()
	hbarAmount := &types.HbarAmount{Value: balance}
	expectedAmount := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		secondSnapshotTimestamp+6,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes1, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAfterThirdSnapshot() {
	// given
	truncateTables(domain.CryptoTransfer{})
	balance := initialAccountBalance + sum(cryptoTransferAmounts)
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, secondSnapshotTimestamp).
		AddAccountBalance(suite.treasuryEntityId.EncodedId, 2_000_000_000).
		AddAccountBalance(suite.getEntityId(accountNum1).EncodedId, balance).
		Persist()
	// No balance info for accountNum1 in the third snapshot due to dedup, i.e., accountNum1's balances at
	// thirdSnapshotTimestamp is the same as the balance at secondSnapshotTimestamp
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, thirdSnapshotTimestamp).
		AddAccountBalance(suite.treasuryEntityId.EncodedId, 2_000_000_000).
		Persist()
	// Add a crypto transfer after the third snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(10).
		EntityId(suite.getEntityId(accountNum1).EncodedId).
		Timestamp(thirdSnapshotTimestamp + 1).
		Persist()

	hbarAmount := &types.HbarAmount{Value: balance}
	expectedAmount := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		thirdSnapshotTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes2, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)

	// when
	actualAmounts, accountIdString, publicKey, err = repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		thirdSnapshotTimestamp+1,
	)

	// then
	hbarAmount.Value += 10
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.NotNil(suite.T(), publicKey)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockForDeletedAccount() {
	// given
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum1).EncodedId, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
	}
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	// account is deleted before the third account balance file, so there is no balance info in the file. querying the
	// account balance for a timestamp after the third account balance file should then return the balance at the time
	// the account is deleted
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		thirdSnapshotTimestamp+10,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes2, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAtAccountDeletionTime() {
	// given
	tdomain.NewEntityBuilder(dbClient, suite.getEntityId(accountNum1).EncodedId, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
	}
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		accountDeleteTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes2, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// given
	truncateTables(domain.Entity{})
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), publicKey)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoInitialBalance() {
	// given
	dbClient.GetDb().Where("account_id <> ?", suite.treasuryEntityId).Delete(&domain.AccountBalance{})

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}

	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.Equal(suite.T(), suite.publicKeyBytes2, publicKey)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), publicKey)
	assert.Nil(suite.T(), actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockDbConnectionError() {
	// given
	repo := NewAccountRepository(invalidDbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Empty(suite.T(), accountIdString)
	assert.Empty(suite.T(), publicKey)
	assert.Nil(suite.T(), actualAmounts)
}

func sum(amounts []int64) int64 {
	var value int64
	for _, amount := range amounts {
		value += amount
	}

	return value
}

// run the suite
func TestAccountRepositoryWithAliasSuite(t *testing.T) {
	suite.Run(t, new(accountRepositoryWithAliasSuite))
}

type accountRepositoryWithAliasSuite struct {
	accountRepositorySuite
	publicKey types.PublicKey
}

func (suite *accountRepositoryWithAliasSuite) SetupSuite() {
	suite.accountRepositorySuite.SetupSuite()

	sk, err := hiero.PrivateKeyGenerateEd25519()
	if err != nil {
		panic(err)
	}
	suite.publicKey = types.PublicKey{PublicKey: sk.PublicKey()}
	suite.accountAlias, _, err = suite.publicKey.ToAlias()
	if err != nil {
		panic(err)
	}
	suite.accountId, err = types.NewAccountIdFromAlias(suite.accountAlias, 0, 0)
	if err != nil {
		panic(err)
	}

	suite.account3Alias = account3Alias
	suite.account4Alias = account4Alias
	suite.account5Alias = account5Alias
	suite.account6Alias = account6Alias
}

func (suite *accountRepositoryWithAliasSuite) SetupTest() {
	suite.accountRepositorySuite.SetupTest()

	// add accountNum2 with the same alias but was deleted before accountNum1
	// the entity row with deleted = true in entity table
	accountId2 := MustEncodeEntityId(suite.shard, suite.realm, accountNum2).EncodedId
	tdomain.NewEntityBuilder(dbClient, accountId2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Deleted(true).
		ModifiedTimestamp(account2DeletedTimestamp).
		Persist()
	// the historical entry
	tdomain.NewEntityBuilder(dbClient, accountId2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		TimestampRange(account2CreatedTimestamp, account2DeletedTimestamp).
		Historical(true).
		Persist()
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountAlias() {
	tests := []struct {
		encodedId     int64
		expectedAlias []byte
	}{
		{encodedId: accountNum3, expectedAlias: account3Alias},
		{encodedId: accountNum4, expectedAlias: account4Alias},
	}

	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expectedAlias, actual.GetAlias())
		})
	}
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountAliasWithInvalidAlias() {
	tests := []struct {
		encodedId int64
		expected  string
	}{
		{encodedId: accountNum5, expected: fmt.Sprintf("0.0.%d", accountNum5)},
		{encodedId: accountNum6, expected: fmt.Sprintf("0.0.%d", accountNum6)},
	}

	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual.String())
		})
	}
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountId() {
	// given
	aliasAccountId, err := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	assert.NoError(suite.T(), err)
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)
	expected := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(accountNum4))

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.Nil(suite.T(), rErr)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountIdDeleted() {
	// given
	tdomain.NewEntityBuilder(dbClient, accountNum4, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), rErr)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// whey querying by alias and the account is not found, expect 0 hbar balance returned
	truncateTables(domain.Entity{})
	repo := NewAccountRepository(dbClient, suite.treasuryEntityId)

	// when
	actualAmounts, accountIdString, publicKey, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), types.AmountSlice{&types.HbarAmount{}}, actualAmounts)
	assert.Nil(suite.T(), publicKey)
	assert.Empty(suite.T(), accountIdString)
}
