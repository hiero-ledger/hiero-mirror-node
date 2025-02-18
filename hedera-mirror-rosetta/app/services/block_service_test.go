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

package services

import (
	"context"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const ed25519AliasHex = "0x12205a081255a92b7c262bc2ea3ab7114b8a815345b3cc40f800b2b40914afecc44e"

var (
	ed25519Alias    = hexutil.MustDecode(ed25519AliasHex)
	accountEntityId = domain.MustDecodeEntityId(500)
	account         = types.NewAccountIdFromEntityId(accountEntityId)
	accountAlias, _ = types.NewAccountIdFromEntity(domain.Entity{Alias: ed25519Alias, Id: accountEntityId})
	entityId        = domain.MustDecodeEntityId(600)
	hbarAmount      = types.HbarAmount{Value: 300}
	statusSuccess   = types.GetTransactionResult(22)
)

func block() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "12345",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         2,
		ParentHash:          "parenthash",
	}
}

func blockRequest() *rTypes.BlockRequest {
	index := int64(100)
	hash := "somehashh"

	return &rTypes.BlockRequest{
		NetworkIdentifier: &rTypes.NetworkIdentifier{
			Blockchain:           "Hedera",
			Network:              "hhh",
			SubNetworkIdentifier: nil,
		},
		BlockIdentifier: &rTypes.PartialBlockIdentifier{
			Index: &index,
			Hash:  &hash,
		},
	}
}

func expectedBlockResponse(
	transactions []*rTypes.Transaction,
	otherTransactions []*rTypes.TransactionIdentifier,
) *rTypes.BlockResponse {
	return &rTypes.BlockResponse{
		Block: &rTypes.Block{
			BlockIdentifier: &rTypes.BlockIdentifier{
				Index: 1,
				Hash:  "0x12345",
			},
			ParentBlockIdentifier: &rTypes.BlockIdentifier{
				Index: 2,
				Hash:  "0xparenthash",
			},
			Timestamp:    1,
			Transactions: transactions,
			Metadata:     nil,
		},
		OtherTransactions: otherTransactions,
	}
}

func expectedTransaction(accountId types.AccountId, entityId *domain.EntityId, hash string) *rTypes.Transaction {
	response := &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: hash},
		Operations: []*rTypes.Operation{
			{
				OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
				Type:                types.GetTransactionType(14),
				Status:              &statusSuccess,
				Account:             accountId.ToRosetta(),
				Amount:              hbarAmount.ToRosetta(),
			},
		},
		Metadata: map[string]interface{}{},
	}
	if entityId != nil {
		response.Metadata = map[string]interface{}{"entity_id": entityId.String()}
	}

	return response
}

func makeTransaction(entityId *domain.EntityId, hash string) *types.Transaction {
	return &types.Transaction{
		EntityId: entityId,
		Hash:     hash,
		Operations: types.OperationSlice{
			{
				AccountId: account,
				Amount:    &hbarAmount,
				Status:    statusSuccess,
				Type:      types.GetTransactionType(14),
			},
		},
	}
}

func transactionRequest() *rTypes.BlockTransactionRequest {
	return &rTypes.BlockTransactionRequest{
		NetworkIdentifier: &rTypes.NetworkIdentifier{
			Blockchain: "someblockchain",
			Network:    "somenetwork",
			SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
				Network:  "somesubnetwork",
				Metadata: nil,
			},
		},
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "someblockhash",
		},
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: "somehash"},
	}
}

func TestBlockServiceSuite(t *testing.T) {
	suite.Run(t, new(blockServiceSuite))
}

type blockServiceSuite struct {
	suite.Suite
	blockService        server.BlockAPIServicer
	cancelCtx           context.CancelFunc
	ctx                 context.Context
	mockAccountRepo     *mocks.MockAccountRepository
	mockBlockRepo       *mocks.MockBlockRepository
	mockTransactionRepo *mocks.MockTransactionRepository
}

func (suite *blockServiceSuite) SetupSuite() {
	suite.ctx, suite.cancelCtx = context.WithCancel(context.Background())
}

func (suite *blockServiceSuite) TearDownSuite() {
	suite.cancelCtx()
}

func (suite *blockServiceSuite) SetupTest() {
	suite.mockAccountRepo = &mocks.MockAccountRepository{}
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.blockService = NewBlockAPIService(
		suite.mockAccountRepo,
		baseService,
		config.Cache{MaxSize: 1024},
		4,
		suite.ctx,
	)
}

func (suite *blockServiceSuite) TestNewBlockAPIService() {
	assert.IsType(suite.T(), &blockAPIService{}, suite.blockService)
}

func (suite *blockServiceSuite) TestBlock() {
	// given:
	exampleTransactions := []*types.Transaction{
		makeTransaction(nil, "123"),
		makeTransaction(&entityId, "246"),
	}
	expected := expectedBlockResponse([]*rTypes.Transaction{
		expectedTransaction(account, nil, "123"),
		expectedTransaction(account, &entityId, "246"),
	}, nil)
	suite.mockAccountRepo.On("GetAccountAlias").Return(account, mocks.NilError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	actual, e := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), expected, actual)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockWithCappedTransactions() {
	// given:
	exampleTransactions := []*types.Transaction{
		makeTransaction(nil, "123"),
		makeTransaction(&entityId, "246"),
		makeTransaction(nil, "333"),
		makeTransaction(nil, "444"),
		makeTransaction(nil, "555"),
	}
	expected := expectedBlockResponse([]*rTypes.Transaction{
		expectedTransaction(account, nil, "123"),
		expectedTransaction(account, &entityId, "246"),
		expectedTransaction(account, nil, "333"),
		expectedTransaction(account, nil, "444"),
	},
		[]*rTypes.TransactionIdentifier{{Hash: "555"}},
	)
	suite.mockAccountRepo.On("GetAccountAlias").Return(account, mocks.NilError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	actual, e := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), expected, actual)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockWithAccountAlias() {
	// given:
	exampleTransactions := []*types.Transaction{
		makeTransaction(nil, "123"),
		makeTransaction(&entityId, "246"),
	}
	expected := expectedBlockResponse([]*rTypes.Transaction{
		expectedTransaction(accountAlias, nil, "123"),
		expectedTransaction(accountAlias, &entityId, "246"),
	}, nil)
	suite.mockAccountRepo.On("GetAccountAlias").Return(accountAlias, mocks.NilError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	actual, e := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), expected, actual)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenAccountRepoFail() {
	// given:
	exampleTransactions := []*types.Transaction{
		makeTransaction(nil, "123"),
		makeTransaction(&entityId, "246"),
	}
	suite.mockAccountRepo.On("GetAccountAlias").Return(types.AccountId{}, errors.ErrInternalServerError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return(exampleTransactions, mocks.NilError)

	// when:
	actual, e := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), e)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(
		mocks.NilBlock,
		&rTypes.Error{},
	)

	// when:
	actual, err := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockThrowsWhenFindBetweenFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindBetween").Return([]*types.Transaction{}, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.Block(nil, blockRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockTransaction() {
	// given:
	exampleTransaction := makeTransaction(nil, "somehash")
	expected := &rTypes.BlockTransactionResponse{Transaction: expectedTransaction(account, nil, "somehash")}

	suite.mockAccountRepo.On("GetAccountAlias").Return(account, mocks.NilError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(exampleTransaction, mocks.NilError)

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockTransactionWithAccountAlias() {
	// given:
	exampleTransaction := makeTransaction(nil, "somehash")
	expected := &rTypes.BlockTransactionResponse{Transaction: expectedTransaction(accountAlias, nil, "somehash")}

	suite.mockAccountRepo.On("GetAccountAlias").Return(accountAlias, mocks.NilError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(exampleTransaction, mocks.NilError)

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenAccountRepoFail() {
	// given:
	exampleTransaction := makeTransaction(nil, "somehash")

	suite.mockAccountRepo.On("GetAccountAlias").Return(types.AccountId{}, errors.ErrInternalServerError)
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(exampleTransaction, mocks.NilError)

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
	suite.mockAccountRepo.AssertNumberOfCalls(suite.T(), "GetAccountAlias", 1)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByIdentifierFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *blockServiceSuite) TestBlockTransactionThrowsWhenFindByHashInBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(mocks.NilTransaction, &rTypes.Error{})

	// when:
	actual, err := suite.blockService.BlockTransaction(nil, transactionRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}
