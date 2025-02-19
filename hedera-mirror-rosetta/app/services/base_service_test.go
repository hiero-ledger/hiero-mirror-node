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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	defaultContext = context.Background()
	exampleHash    = "0x12345"
	exampleIndex   = int64(1)
)

func transaction() *types.Transaction {
	return &types.Transaction{
		Hash:       exampleHash,
		Operations: nil,
	}
}

func transactions() []*types.Transaction {
	return []*types.Transaction{
		{
			Hash:       exampleHash,
			Operations: nil,
		},
		{
			Hash:       exampleHash,
			Operations: nil,
		},
	}
}

func examplePartialBlockIdentifier(index *int64, hash *string) *rTypes.PartialBlockIdentifier {
	return &rTypes.PartialBlockIdentifier{
		Index: index,
		Hash:  hash,
	}
}

func TestBaseServiceSuite(t *testing.T) {
	suite.Run(t, new(onlineBaseServiceSuite))
	suite.Run(t, new(offlineBaseServiceSuite))
}

type onlineBaseServiceSuite struct {
	suite.Suite
	baseService         BaseService
	mockBlockRepo       *mocks.MockBlockRepository
	mockTransactionRepo *mocks.MockTransactionRepository
}

func (suite *onlineBaseServiceSuite) SetupTest() {
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.baseService = baseService
}

func (suite *onlineBaseServiceSuite) TestIsOnline() {
	assert.True(suite.T(), suite.baseService.IsOnline())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.RetrieveBlock(
		defaultContext,
		examplePartialBlockIdentifier(&exampleIndex, &exampleHash),
	)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockByEmptyIdentifier() {
	// given
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)

	// when:
	actual, err := suite.baseService.RetrieveBlock(defaultContext, examplePartialBlockIdentifier(nil, nil))

	// then:
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), block(), actual)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockThrowsFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.RetrieveBlock(
		defaultContext,
		examplePartialBlockIdentifier(&exampleIndex, &exampleHash),
	)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockFindByIndex() {
	// given:
	suite.mockBlockRepo.On("FindByIndex").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.RetrieveBlock(defaultContext, examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockThrowsFindByIndex() {
	// given:
	suite.mockBlockRepo.On("FindByIndex").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.RetrieveBlock(defaultContext, examplePartialBlockIdentifier(&exampleIndex, nil))

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockFindByHash() {
	// given:
	suite.mockBlockRepo.On("FindByHash").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.RetrieveBlock(defaultContext, examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveBlockThrowsFindByHash() {
	// given:
	suite.mockBlockRepo.On("FindByHash").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.RetrieveBlock(defaultContext, examplePartialBlockIdentifier(nil, &exampleHash))

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveSecondLatest() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.RetrieveLatest(defaultContext)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveSecondLatestThrows() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.RetrieveLatest(defaultContext)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveGenesis() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.RetrieveGenesis(defaultContext)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestRetrieveGenesisThrows() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.RetrieveGenesis(defaultContext)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindByIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)

	// when:
	res, e := suite.baseService.FindByIdentifier(defaultContext, exampleIndex, exampleHash)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), block(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindByIdentifierThrows() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.baseService.FindByIdentifier(defaultContext, exampleIndex, exampleHash)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindByHashInBlock() {
	// given:
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(transaction(), mocks.NilError)

	// when:
	res, e := suite.baseService.FindByHashInBlock(defaultContext, exampleHash, 1, 2)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), transaction(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindByHashInBlockThrows() {
	// given:
	suite.mockTransactionRepo.On("FindByHashInBlock").Return(mocks.NilTransaction, &rTypes.Error{})

	// when:
	res, e := suite.baseService.FindByHashInBlock(defaultContext, exampleHash, 1, 2)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindBetween() {
	// given:
	suite.mockTransactionRepo.On("FindBetween").Return(transactions(), mocks.NilError)

	// when:
	res, e := suite.baseService.FindBetween(defaultContext, 1, 2)

	// then:
	assert.Nil(suite.T(), e)
	assert.Equal(suite.T(), transactions(), res)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

func (suite *onlineBaseServiceSuite) TestFindBetweenThrows() {
	// given:
	suite.mockTransactionRepo.On("FindBetween").Return([]*types.Transaction{}, &rTypes.Error{})

	// when:
	res, e := suite.baseService.FindBetween(defaultContext, 1, 2)

	// then:
	assert.Equal(suite.T(), []*types.Transaction{}, res)
	assert.NotNil(suite.T(), e)
	suite.mockBlockRepo.AssertExpectations(suite.T())
}

type offlineBaseServiceSuite struct {
	suite.Suite
	baseService BaseService
}

func (suite *offlineBaseServiceSuite) SetupTest() {

	baseService := NewOfflineBaseService()
	suite.baseService = baseService
}

func (suite *offlineBaseServiceSuite) TestIsOnline() {
	assert.False(suite.T(), suite.baseService.IsOnline())
}

func (suite *offlineBaseServiceSuite) TestFindByHashInBlock() {
	res, err := suite.baseService.FindByHashInBlock(defaultContext, exampleHash, 1, 2)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}

func (suite *offlineBaseServiceSuite) TestFindBetween() {
	res, err := suite.baseService.FindBetween(defaultContext, 1, 1)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}

func (suite *offlineBaseServiceSuite) TestFindByIdentifier() {
	res, err := suite.baseService.FindByIdentifier(defaultContext, exampleIndex, exampleHash)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}

func (suite *offlineBaseServiceSuite) TestRetrieveBlock() {
	res, err := suite.baseService.RetrieveBlock(
		defaultContext,
		examplePartialBlockIdentifier(&exampleIndex, &exampleHash),
	)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}

func (suite *offlineBaseServiceSuite) TestRetrieveGenesis() {
	res, err := suite.baseService.RetrieveGenesis(defaultContext)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}

func (suite *offlineBaseServiceSuite) TestRetrieveLatest() {
	res, err := suite.baseService.RetrieveLatest(defaultContext)
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrInternalServerError, err)
}
