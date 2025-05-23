// SPDX-License-Identifier: Apache-2.0

package persistence

import (
	"encoding/hex"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
	tdomain "github.com/hiero-ledger/hiero-mirror-node/rosetta/test/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

const (
	consensusStart int64 = 1000
	consensusEnd   int64 = 1100
	resultSuccess        = "SUCCESS"
)

var defaultTreasuryEntityId = domain.MustDecodeEntityId(2)

func TestCategorizeHbarTransfers(t *testing.T) {
	firstEntityId := domain.MustDecodeEntityId(12345)
	secondEntityId := domain.MustDecodeEntityId(54321)
	thirdEntityId := domain.MustDecodeEntityId(54350)
	feeCollectorEntityId := domain.MustDecodeEntityId(98)
	stakingRewardEntityId := domain.MustDecodeEntityId(800)

	emptyHbarTransfers := []hbarTransfer{}
	tests := []struct {
		name                           string
		hbarTransfers                  []hbarTransfer
		itemizedTransfer               domain.ItemizedTransferSlice
		stakingRewardPayouts           []hbarTransfer
		expectedFeeHbarTransfers       []hbarTransfer
		expectedNonFeeTransfers        []hbarTransfer
		expectedStakingRewardTransfers []hbarTransfer
	}{
		{
			name:                           "empty",
			expectedFeeHbarTransfers:       emptyHbarTransfers,
			expectedNonFeeTransfers:        emptyHbarTransfers,
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "empty non fee transfers",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers:        emptyHbarTransfers,
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "simple transfer lists",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100, EntityId: firstEntityId},
				{Amount: 100, EntityId: secondEntityId},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "non fee transfer not in transaction record",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -100499210447},
				{secondEntityId, 99999999958},
				{nodeEntityId, 2558345},
				{feeCollectorEntityId, 496652144},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100000000000, EntityId: firstEntityId},
				{Amount: 100000000000, EntityId: thirdEntityId},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -499210447},
				{secondEntityId, 99999999958},
				{nodeEntityId, 2558345},
				{feeCollectorEntityId, 496652144},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100000000000},
			},
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "staking reward payout",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 200},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardEntityId, -100},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100, EntityId: firstEntityId},
				{Amount: 100, EntityId: secondEntityId},
			},
			stakingRewardPayouts: []hbarTransfer{{secondEntityId, 100}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{secondEntityId, 100},
				{stakingRewardEntityId, -100},
			},
		},
		{
			name: "staking reward donation",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100, EntityId: firstEntityId},
				{Amount: 100, EntityId: secondEntityId},
				{Amount: -200, EntityId: firstEntityId}, // firstEntityId donates the exact amount of his pending reward
				{Amount: 200, EntityId: stakingRewardEntityId},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -200},
				{stakingRewardEntityId, 200},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardEntityId, -200},
			},
		},
		{
			name: "partial staking reward donation",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -105},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardEntityId, -60},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100, EntityId: firstEntityId},
				{Amount: 100, EntityId: secondEntityId},
				{Amount: -140, EntityId: firstEntityId}, // firstEntityId donates part of his pending reward
				{Amount: 140, EntityId: stakingRewardEntityId},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -140},
				{stakingRewardEntityId, 140},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardEntityId, -200},
			},
		},
		{
			name: "staking reward donation more than pending",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -215},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardEntityId, 50},
			},
			itemizedTransfer: domain.ItemizedTransferSlice{
				{Amount: -100, EntityId: firstEntityId},
				{Amount: 100, EntityId: secondEntityId},
				{Amount: -250, EntityId: firstEntityId}, // firstEntityId donates more than his pending reward
				{Amount: 250, EntityId: stakingRewardEntityId},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -250},
				{stakingRewardEntityId, 250},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardEntityId, -200},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			feeHbarTransfers, adjustedNonFeeTransfers, stakingRewardTransfers := categorizeHbarTransfers(
				tt.hbarTransfers,
				tt.itemizedTransfer,
				stakingRewardEntityId,
				tt.stakingRewardPayouts,
			)
			assert.Equal(t, tt.expectedFeeHbarTransfers, feeHbarTransfers)
			assert.Equal(t, tt.expectedNonFeeTransfers, adjustedNonFeeTransfers)
			assert.Equal(t, tt.expectedStakingRewardTransfers, stakingRewardTransfers)
		})
	}
}

func TestGeneralOperationStatus(t *testing.T) {
	assert.Equal(t, "GENERAL_ERROR", types.GetTransactionResult(10000))
}

func TestTransactionGetHashString(t *testing.T) {
	tx := transaction{Hash: []byte{1, 2, 3, 0xaa, 0xff}}
	assert.Equal(t, "0x010203aaff", tx.getHashString())
}

func TestHbarTransferGetAccount(t *testing.T) {
	hbarTransfer := hbarTransfer{AccountId: defaultTreasuryEntityId}
	assert.Equal(t, defaultTreasuryEntityId, hbarTransfer.getAccountId())
}

func TestHbarTransferGetAmount(t *testing.T) {
	hbarTransfer := hbarTransfer{Amount: 10}
	assert.Equal(t, &types.HbarAmount{Value: 10}, hbarTransfer.getAmount())
}

func assertOperationIndexes(t *testing.T, operations types.OperationSlice) {
	makeRange := func(len int) []int64 {
		result := make([]int64, len)
		for i := range result {
			result[i] = int64(i)
		}
		return result
	}

	expected := makeRange(len(operations))
	actual := make([]int64, len(operations))
	for i := range operations {
		actual[i] = operations[i].Index
		// side effect, clear operation's index
		operations[i].Index = 0
	}

	assert.Equal(t, expected, actual)
}

func assertTransactions(t *testing.T, expected, actual []*types.Transaction) {
	getTransactionMap := func(transactions []*types.Transaction) map[string]*types.Transaction {
		result := make(map[string]*types.Transaction)
		for _, tx := range transactions {
			result[tx.Hash] = tx
		}
		return result
	}

	assert.Len(t, actual, len(expected))

	for _, tx := range actual {
		// assert the 0-based, unique, contiguous operations indexes
		assertOperationIndexes(t, tx.Operations)
	}

	actualTransactionMap := getTransactionMap(actual)
	expectedTransactionMap := getTransactionMap(expected)

	assert.Len(t, actualTransactionMap, len(expectedTransactionMap))

	for txHash, actualTx := range actualTransactionMap {
		assert.Contains(t, expectedTransactionMap, txHash)
		expectedTx := expectedTransactionMap[txHash]
		assert.Equal(t, expectedTx.EntityId, actualTx.EntityId)
		assert.Equal(t, expectedTx.Memo, actualTx.Memo)
		assert.ElementsMatch(t, expectedTx.Operations, actualTx.Operations)
	}
}

func TestTransactionRepositorySuite(t *testing.T) {
	suite.Run(t, new(transactionRepositorySuite))
}

func TestTransactionRepositoryNonDefaultShardRealmSuite(t *testing.T) {
	suite.Run(t, &transactionRepositorySuite{
		shard: 1,
		realm: 5,
	})
}

type transactionRepositorySuite struct {
	integrationTest
	suite.Suite
	firstEntityId         domain.EntityId
	secondEntityId        domain.EntityId
	thirdEntityId         domain.EntityId
	newEntityId           domain.EntityId
	feeCollectorEntityId  domain.EntityId
	stakingRewardEntityId domain.EntityId
	treasuryEntityId      domain.EntityId
	firstAccountId        types.AccountId
	secondAccountId       types.AccountId
	newAccountId          types.AccountId
	nodeAccountId         types.AccountId
	feeCollectorAccountId types.AccountId
	treasuryAccountId     types.AccountId
	tokenId2              domain.EntityId
	tokenId3              domain.EntityId
	shard                 int64
	realm                 int64
}

func (suite *transactionRepositorySuite) SetupSuite() {
	shard := suite.shard
	realm := suite.realm
	suite.firstEntityId = MustEncodeEntityId(shard, realm, 12345)
	suite.secondEntityId = MustEncodeEntityId(shard, realm, 54321)
	suite.thirdEntityId = MustEncodeEntityId(shard, realm, 54350)
	suite.newEntityId = MustEncodeEntityId(shard, realm, 55000)
	suite.feeCollectorEntityId = MustEncodeEntityId(shard, realm, 98)
	suite.stakingRewardEntityId = MustEncodeEntityId(shard, realm, 800)
	suite.treasuryEntityId = MustEncodeEntityId(shard, realm, 2)
	suite.firstAccountId = types.NewAccountIdFromEntityId(suite.firstEntityId)
	suite.secondAccountId = types.NewAccountIdFromEntityId(suite.secondEntityId)
	suite.newAccountId = types.NewAccountIdFromEntityId(suite.newEntityId)
	suite.nodeAccountId = types.NewAccountIdFromEntityId(nodeEntityId)
	suite.feeCollectorAccountId = types.NewAccountIdFromEntityId(suite.feeCollectorEntityId)
	suite.treasuryAccountId = types.NewAccountIdFromEntityId(suite.treasuryEntityId)
	suite.tokenId2 = MustEncodeEntityId(shard, realm, 26700)
	suite.tokenId3 = MustEncodeEntityId(shard, realm, 26750) // nft
}

func (suite *transactionRepositorySuite) TestNewTransactionRepository() {
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)
	assert.NotNil(suite.T(), t)
}

func (suite *transactionRepositorySuite) TestFindBetween() {
	// given
	expected := suite.setupDb()
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenTokenCreatedAtOrBeforeGenesisTimestamp() {
	// given
	genesisTimestamp := int64(100)
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, genesisTimestamp).
		AddAccountBalance(suite.treasuryAccountId.GetId(), 2_000_000).
		Persist()

	transaction := tdomain.NewTransactionBuilder(dbClient, suite.treasuryEntityId.EncodedId, genesisTimestamp+10).Persist()
	transferTimestamp := transaction.ConsensusTimestamp
	// add crypto transfers
	nodeEntityId := MustEncodeEntityId(suite.shard, suite.realm, 3)
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(-20).EntityId(suite.treasuryEntityId.EncodedId).Timestamp(transferTimestamp).Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(20).EntityId(nodeEntityId.EncodedId).Timestamp(transferTimestamp).Persist()

	expected := []*types.Transaction{
		{
			Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transaction.TransactionHash)),
			Memo: []byte{},
			Operations: types.OperationSlice{
				{
					AccountId: suite.treasuryAccountId,
					Amount:    &types.HbarAmount{Value: -20},
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
				{
					AccountId: types.NewAccountIdFromEntityId(nodeEntityId),
					Amount:    &types.HbarAmount{Value: 20},
					Index:     1,
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
			},
		},
	}
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindBetween(defaultContext, transaction.ConsensusTimestamp, transaction.ConsensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenThrowsWhenStartAfterEnd() {
	// given
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusStart-1)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenDbConnectionError() {
	// given
	t := NewTransactionRepository(invalidDbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenUnknownTransactionType() {
	// given
	payer := suite.firstEntityId.EncodedId
	transaction := tdomain.NewTransactionBuilder(dbClient, payer, time.Now().UnixNano()).
		ItemizedTransfer(domain.ItemizedTransferSlice{
			{
				Amount:   -20,
				EntityId: suite.firstEntityId,
			},
			{
				Amount:   20,
				EntityId: suite.secondEntityId,
			},
		}).
		Type(32767).
		Persist()
	// add crypto transfers
	consensusTimestamp := transaction.ConsensusTimestamp
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(-40).EntityId(payer).Timestamp(consensusTimestamp).Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(15).EntityId(3).Timestamp(consensusTimestamp).Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(5).EntityId(suite.feeCollectorEntityId.EncodedId).Timestamp(consensusTimestamp).Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(20).EntityId(suite.secondEntityId.EncodedId).Timestamp(consensusTimestamp).Persist()

	expected := []*types.Transaction{
		{
			Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transaction.TransactionHash)),
			Memo: []byte{},
			Operations: types.OperationSlice{
				{
					AccountId: suite.firstAccountId,
					Amount:    &types.HbarAmount{Value: -20},
					Type:      "UNKNOWN",
					Status:    resultSuccess,
				},
				{
					AccountId: suite.secondAccountId,
					Amount:    &types.HbarAmount{Value: 20},
					Index:     1,
					Type:      "UNKNOWN",
					Status:    resultSuccess,
				},
				{
					AccountId: types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(3)),
					Amount:    &types.HbarAmount{Value: 15},
					Index:     2,
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
				{
					AccountId: suite.feeCollectorAccountId,
					Amount:    &types.HbarAmount{Value: 5},
					Index:     3,
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
				{
					AccountId: suite.firstAccountId,
					Amount:    &types.HbarAmount{Value: -20},
					Index:     4,
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
			},
		},
	}
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindBetween(defaultContext, consensusTimestamp, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlock() {
	// given
	expected := suite.setupDb()
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, expected[0].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[0]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockNoTokenEntity() {
	// given
	expected := suite.setupDb()
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, expected[1].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[1]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsInvalidHash() {
	// given
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "invalid hash", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsNotFound() {
	// given
	t := NewTransactionRepository(dbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "0x123456", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockDbConnectionError() {
	// given
	t := NewTransactionRepository(invalidDbClient, suite.stakingRewardEntityId)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "0x123456", consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) setupDb() []*types.Transaction {
	var consensusTimestamp, validStartNs int64

	tick := func(nanos int64) {
		consensusTimestamp += nanos
		validStartNs += nanos
	}

	// create account balance file
	genesisTimestamp := consensusStart - 200
	tdomain.NewAccountBalanceSnapshotBuilder(dbClient, genesisTimestamp).
		AddAccountBalance(suite.treasuryAccountId.GetId(), 2_000_000).
		Persist()

	// successful crypto transfer transaction
	consensusTimestamp = consensusStart + 1
	validStartNs = consensusStart - 10
	errataTypeInsert := domain.ErrataTypeInsert
	cryptoTransfers := []domain.CryptoTransfer{
		{Amount: -150, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			Errata: &errataTypeInsert, PayerAccountId: suite.firstEntityId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: suite.secondEntityId,
			Errata: &errataTypeInsert, PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	itemizedTransfer := domain.ItemizedTransferSlice{
		{Amount: -135, EntityId: suite.firstEntityId},
		{Amount: 135, EntityId: suite.secondEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId, 22,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, itemizedTransfer,
		[]byte("simple transfer"))

	// duplicate transaction
	consensusTimestamp += 1
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId, 11,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil,
		[]byte("simple transfer"))
	operationType := types.OperationTypeCryptoTransfer
	operations1 := types.OperationSlice{
		{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -135}, Type: operationType, Status: resultSuccess},
		{AccountId: suite.secondAccountId, Amount: &types.HbarAmount{Value: 135}, Type: operationType, Status: resultSuccess},
		{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
	}
	expectedTransaction1 := &types.Transaction{
		Hash:       "0x010203",
		Memo:       []byte("simple transfer"),
		Operations: operations1,
	}

	// a successful crypto transfer
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -230, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: suite.secondEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	itemizedTransfer = domain.ItemizedTransferSlice{
		{Amount: -215, EntityId: suite.firstEntityId},
		{Amount: 215, EntityId: suite.secondEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId, 22,
		[]byte{0xa, 0xb, 0xc}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, itemizedTransfer,
		[]byte{})
	operations2 := types.OperationSlice{
		{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -215}, Type: operationType, Status: resultSuccess},
		{AccountId: suite.secondAccountId, Amount: &types.HbarAmount{Value: 215}, Type: operationType, Status: resultSuccess},
		{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
	}
	expectedTransaction2 := &types.Transaction{Hash: "0x0a0b0c", Memo: []byte{}, Operations: operations2}

	// token create transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &suite.tokenId2, &nodeEntityId, suite.firstEntityId, 22,
		[]byte{0xaa, 0xcc, 0xdd}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil, nil)
	operationType = types.OperationTypeTokenCreate
	expectedTransaction3 := &types.Transaction{
		EntityId: &suite.tokenId2,
		Hash:     "0xaaccdd",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// originally, this was an nft create.  Now it just is fees.
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &suite.tokenId3, &nodeEntityId, suite.firstEntityId, 22,
		[]byte{0xaa, 0x11, 0x22}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil, nil)
	expectedTransaction4 := &types.Transaction{
		EntityId: &suite.tokenId3,
		Hash:     "0xaa1122",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// originally, this was an nft mint.  Now it just is fees.
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &suite.tokenId3, &nodeEntityId, suite.firstEntityId, 22,
		[]byte{0xaa, 0x11, 0x33}, domain.TransactionTypeTokenMint, validStartNs, cryptoTransfers, nil, nil)
	expectedTransaction5 := &types.Transaction{
		EntityId: &suite.tokenId3,
		Hash:     "0xaa1133",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// originally, this was an nft transfer.  Now it just is fees.
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId,
		22, []byte{0xaa, 0x11, 0x66}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil,
		nil)
	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction6 := &types.Transaction{
		Hash: "0xaa1166",
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// a failed crypto transfer due to insufficient account balance, the spurious transfers are marked as 'DELETE'
	tick(1)
	errataTypeDelete := domain.ErrataTypeDelete
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -120, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: -1000000, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId,
			Errata: &errataTypeDelete, PayerAccountId: suite.firstEntityId},
		{Amount: 1000000, ConsensusTimestamp: consensusTimestamp, EntityId: suite.secondEntityId,
			Errata: &errataTypeDelete, PayerAccountId: suite.firstEntityId},
	}
	transactionHash := randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId, 28,
		transactionHash, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil, nil)
	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction7 := &types.Transaction{
		Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{}, Type: types.OperationTypeCryptoTransfer,
				Status: types.GetTransactionResult(28)},
		},
	}

	// crypto create transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -620, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: suite.newEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId, PayerAccountId: suite.firstEntityId},
	}
	itemizedTransfer = domain.ItemizedTransferSlice{
		{Amount: -500, EntityId: suite.firstEntityId},
		{Amount: 500, EntityId: suite.newEntityId},
	}
	transactionHash = randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, &suite.newEntityId, &nodeEntityId, suite.firstEntityId, 22, transactionHash,
		domain.TransactionTypeCryptoCreateAccount, validStartNs, cryptoTransfers, itemizedTransfer, nil)

	operationType = types.OperationTypeCryptoCreateAccount
	expectedTransaction8 := &types.Transaction{
		EntityId: &suite.newEntityId,
		Hash:     tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -500},
				Type: types.OperationTypeCryptoCreateAccount, Status: resultSuccess},
			{AccountId: suite.newAccountId, Amount: &types.HbarAmount{Value: 500},
				Type: types.OperationTypeCryptoCreateAccount, Status: resultSuccess},
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// crypto transfer transaction with staking reward payout
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -520, ConsensusTimestamp: consensusTimestamp, EntityId: suite.firstEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: suite.newEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: suite.feeCollectorEntityId,
			PayerAccountId: suite.firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId, PayerAccountId: suite.firstEntityId},
		{Amount: -100, ConsensusTimestamp: consensusTimestamp, EntityId: suite.stakingRewardEntityId,
			PayerAccountId: suite.firstEntityId},
	}
	itemizedTransfer = domain.ItemizedTransferSlice{
		{Amount: -500, EntityId: suite.firstEntityId},
		{Amount: 500, EntityId: suite.newEntityId},
	}
	transactionHash = randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, suite.firstEntityId, 22, transactionHash,
		domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, itemizedTransfer, nil)
	tdomain.NewStakingRewardTransferBuilder(dbClient).
		AccountId(suite.firstEntityId.EncodedId).
		Amount(100).
		ConsensusTimestamp(consensusTimestamp).
		PayerAccountId(suite.firstEntityId.EncodedId).
		Persist()

	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction9 := &types.Transaction{
		Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -500}, Type: operationType,
				Status: resultSuccess},
			{AccountId: suite.newAccountId, Amount: &types.HbarAmount{Value: 500}, Type: operationType,
				Status: resultSuccess},
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: suite.firstAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeCryptoTransfer,
				Status: resultSuccess},
			{AccountId: types.NewAccountIdFromEntityId(suite.stakingRewardEntityId), Amount: &types.HbarAmount{Value: -100},
				Type: types.OperationTypeCryptoTransfer, Status: resultSuccess},
		},
	}

	return []*types.Transaction{
		expectedTransaction1, expectedTransaction2, expectedTransaction3, expectedTransaction4,
		expectedTransaction5, expectedTransaction6, expectedTransaction7, expectedTransaction8,
		expectedTransaction9,
	}
}
