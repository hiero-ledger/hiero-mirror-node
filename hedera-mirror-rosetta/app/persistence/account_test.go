/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package persistence

import (
	"context"
	"fmt"
	"testing"

	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	tdomain "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

const (
	account1 = int64(9000) + iota
	account2
	account3
	account4
	account5
	account6
)

const (
	accountDeleteTimestamp         = secondSnapshotTimestamp + 180
	account1CreatedTimestamp       = firstSnapshotTimestamp - 100
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
	account3Alias = hexutil.MustDecode("0x3a2103d9a822b91df7850274273a338c152e7bcfa2036b24cd9e3b29d07efd949b387a")
	account4Alias = hexutil.MustDecode("0x12205a081255a92b7c262bc2ea3ab7114b8a815345b3cc40f800b2b40914afecc44e")
	account5Alias = randstr.Bytes(48)
	// alias with invalid public key, the Key message is valid, but it's formed from an invalid 16-byte ED25519 pub key
	account6Alias = hexutil.MustDecode("0x1210815345b3cc40f800b2b40914afecc44e")
)

// run the suite
func TestAccountRepositorySuite(t *testing.T) {
	suite.Run(t, new(accountRepositorySuite))
}

type accountRepositorySuite struct {
	integrationTest
	suite.Suite
	accountId       types.AccountId
	accountIdString string
	accountAlias    []byte
	account3Alias   []byte
	account4Alias   []byte
	account5Alias   []byte
	account6Alias   []byte
}

func (suite *accountRepositorySuite) SetupSuite() {
	suite.accountId = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account1))
	suite.accountIdString = suite.accountId.String()
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()

	tdomain.NewEntityBuilder(dbClient, account1, account1CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Persist()

	// account balance files, always add an account_balance row for treasury
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, firstSnapshotTimestamp).
		AddAccountBalance(treasuryAccountId.GetId(), 2_000_000_000).
		AddAccountBalance(account1, initialAccountBalance).
		Persist()
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, thirdSnapshotTimestamp).
		AddAccountBalance(treasuryAccountId.GetId(), 2_000_000_000).
		Persist()

	// crypto transfers happened at <= first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(110).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp - 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(170).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp).
		Persist()

	// crypto transfers happened at > first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[0]).
		EntityId(account1).
		Errata(domain.ErrataTypeInsert).
		Timestamp(firstSnapshotTimestamp + 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(12345).
		EntityId(account1).
		Errata(domain.ErrataTypeDelete).
		Timestamp(firstSnapshotTimestamp + 2).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[1]).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp + 5).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(-(initialAccountBalance + sum(cryptoTransferAmounts))).
		EntityId(account1).
		Timestamp(accountDeleteTimestamp).
		Persist()

	// accounts for GetAccountAlias tests
	tdomain.NewEntityBuilder(dbClient, account3, account3CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account3Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account4, account4CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account4Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account5, account5CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account5Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account6, account6CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account6Alias).
		Persist()
}

func (suite *accountRepositorySuite) TestGetAccountAlias() {
	tests := []struct {
		encodedId int64
		expected  string
	}{
		{encodedId: account3, expected: fmt.Sprintf("0.0.%d", account3)},
		{encodedId: account4, expected: fmt.Sprintf("0.0.%d", account4)},
	}

	repo := NewAccountRepository(dbClient)

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
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account3))
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.GetAccountAlias(defaultContext, accountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountId() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdNumericAccount() {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account1))
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, accountId)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), accountId, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdDbConnectionError() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	// transfers before or at the snapshot timestamp should not affect balance calculation
	accountId := suite.accountId
	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}

	// when
	// query
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(defaultContext, accountId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAfterSecondSnapshot() {
	// given
	// remove any transfers in db. with the balance info in the second snapshot, this test verifies the account balance
	// is directly read from the snapshot
	accountId := suite.accountId
	truncateTables(domain.CryptoTransfer{})
	balance := initialAccountBalance + sum(cryptoTransferAmounts)
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, secondSnapshotTimestamp).
		AddAccountBalance(treasuryAccountId.GetId(), 2_000_000_000).
		AddAccountBalance(account1, balance).
		Persist()
	hbarAmount := &types.HbarAmount{Value: balance}
	expectedAmount := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		secondSnapshotTimestamp+6,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAfterThirdSnapshot() {
	// given
	accountId := suite.accountId
	truncateTables(domain.CryptoTransfer{})
	balance := initialAccountBalance + sum(cryptoTransferAmounts)
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, secondSnapshotTimestamp).
		AddAccountBalance(treasuryAccountId.GetId(), 2_000_000_000).
		AddAccountBalance(account1, balance).
		Persist()
	// No balance info for account1 in the third snapshot due to dedup, i.e., account1's balances at
	// thirdSnapshotTimestamp is the same as the balance at secondSnapshotTimestamp
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, thirdSnapshotTimestamp).
		AddAccountBalance(treasuryAccountId.GetId(), 2_000_000_000).
		Persist()
	// Add a crypto transfer after the third snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(10).
		EntityId(account1).
		Timestamp(thirdSnapshotTimestamp + 1).
		Persist()

	hbarAmount := &types.HbarAmount{Value: balance}
	expectedAmount := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		thirdSnapshotTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)

	// when
	actualAmounts, accountIdString, err = repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		thirdSnapshotTimestamp+1,
	)

	// then
	hbarAmount.Value += 10
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockForDeletedAccount() {
	// given
	accountId := suite.accountId
	tdomain.NewEntityBuilder(dbClient, account1, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
	}
	repo := NewAccountRepository(dbClient)

	// when
	// account is deleted before the third account balance file, so there is no balance info in the file. querying the
	// account balance for a timestamp after the third account balance file should then return the balance at the time
	// the account is deleted
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		thirdSnapshotTimestamp+10,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAtAccountDeletionTime() {
	// given
	accountId := suite.accountId
	tdomain.NewEntityBuilder(dbClient, account1, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
	}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		accountDeleteTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// given
	accountId := suite.accountId
	truncateTables(domain.Entity{})
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoInitialBalance() {
	// given
	accountId := suite.accountId
	dbClient.GetDb().Where("account_id <> ?", treasuryAccountId.GetId()).Delete(&domain.AccountBalance{})

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}

	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	accountId := suite.accountId
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockDbConnectionError() {
	// given
	accountId := suite.accountId
	repo := NewAccountRepository(invalidDbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Empty(suite.T(), accountIdString)
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

	// add account2 with the same alias but was deleted before account1
	// the entity row with deleted = true in entity table
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Deleted(true).
		ModifiedTimestamp(account2DeletedTimestamp).
		Persist()
	// the historical entry
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
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
		{encodedId: account3, expectedAlias: account3Alias},
		{encodedId: account4, expectedAlias: account4Alias},
	}

	repo := NewAccountRepository(dbClient)

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
		{encodedId: account5, expected: fmt.Sprintf("0.0.%d", account5)},
		{encodedId: account6, expected: fmt.Sprintf("0.0.%d", account6)},
	}

	repo := NewAccountRepository(dbClient)

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
	repo := NewAccountRepository(dbClient)
	expected := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account4))

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.Nil(suite.T(), rErr)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountIdDeleted() {
	// given
	tdomain.NewEntityBuilder(dbClient, account4, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient)

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), rErr)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// whey querying by alias and the account is not found, expect 0 hbar balance returned
	truncateTables(domain.Entity{})
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), types.AmountSlice{&types.HbarAmount{}}, actualAmounts)
	assert.Empty(suite.T(), accountIdString)
}
