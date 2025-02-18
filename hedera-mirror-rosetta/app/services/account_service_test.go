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
	"encoding/hex"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	tdomain "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func amount() types.AmountSlice {
	return types.AmountSlice{
		&types.HbarAmount{Value: int64(1000)},
	}
}

func getAccountBalanceRequest(customizers ...func(*rTypes.AccountBalanceRequest)) *rTypes.AccountBalanceRequest {
	index := int64(1)
	hash := "0x123"
	blockIdentifier := &rTypes.PartialBlockIdentifier{
		Index: &index,
		Hash:  &hash,
	}
	r := &rTypes.AccountBalanceRequest{
		AccountIdentifier: &rTypes.AccountIdentifier{Address: "0.0.1"},
		BlockIdentifier:   blockIdentifier,
	}
	for _, customize := range customizers {
		customize(r)
	}
	return r
}

func accountBalanceRequestRemoveBlockIdentifier(request *rTypes.AccountBalanceRequest) {
	request.BlockIdentifier = nil
}

func accountBalanceRequestUseAccount(account string) func(response *rTypes.AccountBalanceRequest) {
	return func(request *rTypes.AccountBalanceRequest) {
		request.AccountIdentifier.Address = account
	}
}

func expectedAccountBalanceResponse(customizers ...func(*rTypes.AccountBalanceResponse)) *rTypes.AccountBalanceResponse {
	response := &rTypes.AccountBalanceResponse{
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "0x12345",
		},
		Balances: []*rTypes.Amount{
			{
				Value:    "1000",
				Currency: types.CurrencyHbar,
			},
		},
	}
	for _, customize := range customizers {
		customize(response)
	}
	return response
}

func accountBalanceResponseMetadata(metadata map[string]interface{}) func(*rTypes.AccountBalanceResponse) {
	return func(response *rTypes.AccountBalanceResponse) {
		response.Metadata = metadata
	}
}

func TestAccountServiceSuite(t *testing.T) {
	suite.Run(t, new(accountServiceSuite))
}

type accountServiceSuite struct {
	suite.Suite
	accountService      server.AccountAPIServicer
	mockAccountRepo     *mocks.MockAccountRepository
	mockBlockRepo       *mocks.MockBlockRepository
	mockTransactionRepo *mocks.MockTransactionRepository
}

func (suite *accountServiceSuite) SetupTest() {
	suite.mockAccountRepo = &mocks.MockAccountRepository{}
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.accountService = NewAccountAPIService(baseService, suite.mockAccountRepo, 0, 0)
}

func (suite *accountServiceSuite) TestAccountBalance() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), "", mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(
		defaultContext,
		getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier),
	)

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAliasAccountBalance() {
	// given:
	accountId := "0.0.100"
	_, pk := tdomain.GenEd25519KeyPair()
	alias := ed25519AliasPrefix + hex.EncodeToString(pk.BytesRaw())
	metadata := map[string]interface{}{"account_id": accountId}
	suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), accountId, mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(
		defaultContext,
		getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier, accountBalanceRequestUseAccount(alias)),
	)

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(accountBalanceResponseMetadata(metadata)), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), "", mocks.NilError)

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, getAccountBalanceRequest())

	// then:
	assert.Equal(suite.T(), expectedAccountBalanceResponse(), actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext,
		getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier))

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, getAccountBalanceRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBalanceAtBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(types.AmountSlice{}, "", &rTypes.Error{})

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, getAccountBalanceRequest())

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenAddressInvalid() {
	for _, invalidAddress := range []string{"abc", "-1"} {
		suite.T().Run(invalidAddress, func(t *testing.T) {
			// given
			// when
			actual, err := suite.accountService.AccountBalance(
				defaultContext,
				getAccountBalanceRequest(accountBalanceRequestUseAccount(invalidAddress)),
			)

			// then
			assert.Equal(t, errors.ErrInvalidAccount, err)
			assert.Nil(t, actual)

		})
	}
}

func (suite *accountServiceSuite) TestAccountCoins() {
	// when:
	result, err := suite.accountService.AccountCoins(defaultContext, &rTypes.AccountCoinsRequest{})

	// then:
	assert.Equal(suite.T(), errors.ErrNotImplemented, err)
	assert.Nil(suite.T(), result)
}
