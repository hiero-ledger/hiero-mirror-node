// SPDX-License-Identifier: Apache-2.0

package services

import (
	"encoding/hex"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/mocks"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/utils"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
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

func getAllAccountBalancesRequest(request *rTypes.AccountBalanceRequest) *rTypes.AllAccountBalancesRequest {
	return &rTypes.AllAccountBalancesRequest{
		NetworkIdentifier: request.NetworkIdentifier,
		AccountIdentifier: request.AccountIdentifier,
		BlockIdentifier:   request.BlockIdentifier,
		Currencies:        request.Currencies,
	}
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
		Metadata: map[string]interface{}{},
	}
	for _, customizer := range customizers {
		if customizer != nil {
			customizer(response)
		}
	}
	return response
}

func expectedAllAccountBalancesResponse(response *rTypes.AccountBalanceResponse) *rTypes.AllAccountBalancesResponse {
	return &rTypes.AllAccountBalancesResponse{
		BlockIdentifier: response.BlockIdentifier,
		AccountBalances: []*rTypes.AccountBalanceWithSubAccount{
			{
				Balances: response.Balances,
				Metadata: response.Metadata,
			},
		},
	}
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
	publicKey := utils.MustMarshal(utils.Ed25519PublicKey())
	metadataCustomizer := accountBalanceResponseMetadata(map[string]interface{}{
		"public_key": tools.SafeAddHexPrefix(hex.EncodeToString(publicKey)),
	})
	tests := []struct {
		publicKey          []byte
		metadataCustomizer func(*rTypes.AccountBalanceResponse)
	}{
		{
			publicKey:          publicKey,
			metadataCustomizer: metadataCustomizer,
		},
		{
			publicKey: nil,
		},
		{
			publicKey: make([]byte, 0),
		},
		{
			publicKey: []byte{0x12},
		},
		{
			publicKey: []byte{0x12, 0x20, 0xff},
		},
		{
			publicKey: append([]byte{0x12, 0x21}, randstr.Bytes(32)...),
		},
		{
			publicKey: utils.MustMarshal(utils.EcdsaSecp256k1PublicKey()),
		},
		{
			publicKey: utils.MustMarshal(utils.KeyListKey([]*services.Key{utils.Ed25519PublicKey(), utils.EcdsaSecp256k1PublicKey()})),
		},
	}

	for _, tt := range tests {
		suite.T().Run(hex.EncodeToString(tt.publicKey), func(t *testing.T) {
			// given:
			// calling SetupTest explicitly so for every test, the mocks are fresh
			suite.SetupTest()
			suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
			suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), "", tt.publicKey, mocks.NilError)
			request := getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier)
			response := expectedAccountBalanceResponse(tt.metadataCustomizer)

			// when:
			actual, err := suite.accountService.AccountBalance(defaultContext, request)

			// then:
			assert.Equal(suite.T(), response, actual)
			assert.Nil(suite.T(), err)
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")

			// when:
			allBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

			// then:
			assert.Equal(suite.T(), expectedAllAccountBalancesResponse(response), allBalancesResponse)
			assert.Nil(suite.T(), err)
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
		})
	}
}

func (suite *accountServiceSuite) TestAliasAccountBalance() {
	accountId := "0.0.100"
	publicKey := utils.MustMarshal(utils.Ed25519PublicKey())
	alias := tools.SafeAddHexPrefix(hex.EncodeToString(publicKey))
	accountIdMetadataCustomizer := accountBalanceResponseMetadata(
		map[string]interface{}{"account_id": accountId})
	fullMetadataCustomizer := accountBalanceResponseMetadata(map[string]interface{}{
		"account_id": accountId,
		"public_key": alias,
	})
	tests := []struct {
		publicKey          []byte
		metadataCustomizer func(*rTypes.AccountBalanceResponse)
	}{
		{
			publicKey:          publicKey,
			metadataCustomizer: fullMetadataCustomizer,
		},
		{
			publicKey:          nil,
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          make([]byte, 0),
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          []byte{0x12},
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          []byte{0x12, 0x20, 0xff},
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          append([]byte{0x12, 0x21}, randstr.Bytes(32)...),
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          utils.MustMarshal(utils.EcdsaSecp256k1PublicKey()),
			metadataCustomizer: accountIdMetadataCustomizer,
		},
		{
			publicKey:          utils.MustMarshal(utils.KeyListKey([]*services.Key{utils.Ed25519PublicKey(), utils.EcdsaSecp256k1PublicKey()})),
			metadataCustomizer: accountIdMetadataCustomizer,
		},
	}

	for _, tt := range tests {
		suite.T().Run(hex.EncodeToString(tt.publicKey), func(t *testing.T) {
			// given
			// calling SetupTest explicitly so for every test, the mocks are fresh
			suite.SetupTest()
			suite.mockBlockRepo.On("RetrieveLatest").Return(block(), mocks.NilError)
			suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), accountId, tt.publicKey, mocks.NilError)
			request := getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier, accountBalanceRequestUseAccount(alias))
			response := expectedAccountBalanceResponse(tt.metadataCustomizer)

			// when
			actual, err := suite.accountService.AccountBalance(defaultContext, request)

			// then
			assert.Equal(suite.T(), response, actual)
			assert.Nil(suite.T(), err)
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")

			// when
			allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

			// then
			assert.Equal(suite.T(), expectedAllAccountBalancesResponse(response), allAccountBalancesResponse)
			assert.Nil(suite.T(), err)
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByIdentifier")
			suite.mockBlockRepo.AssertNotCalled(suite.T(), "FindByHash")
		})
	}
}

func (suite *accountServiceSuite) TestAccountBalanceWithBlockIdentifier() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(amount(), "", []byte{}, mocks.NilError)
	request := getAccountBalanceRequest()
	response := expectedAccountBalanceResponse()

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, request)

	// then:
	assert.Equal(suite.T(), response, actual)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")

	// when:
	allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

	// then:
	assert.Equal(suite.T(), expectedAllAccountBalancesResponse(response), allAccountBalancesResponse)
	assert.Nil(suite.T(), err)
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveLatest").Return(mocks.NilBlock, &rTypes.Error{})
	request := getAccountBalanceRequest(accountBalanceRequestRemoveBlockIdentifier)

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, request)

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")

	// when:
	allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

	// then:
	assert.Nil(suite.T(), allAccountBalancesResponse)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(mocks.NilBlock, &rTypes.Error{})
	request := getAccountBalanceRequest()

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, request)

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")

	// when:
	allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

	// then:
	assert.Nil(suite.T(), allAccountBalancesResponse)
	assert.NotNil(suite.T(), err)
	suite.mockAccountRepo.AssertNotCalled(suite.T(), "RetrieveBalanceAtBlock")
	suite.mockBlockRepo.AssertNotCalled(suite.T(), "RetrieveLatest")
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenRetrieveBalanceAtBlockFails() {
	// given:
	suite.mockBlockRepo.On("FindByIdentifier").Return(block(), mocks.NilError)
	suite.mockAccountRepo.On("RetrieveBalanceAtBlock").Return(types.AmountSlice{}, "", []byte{}, &rTypes.Error{})
	request := getAccountBalanceRequest()

	// when:
	actual, err := suite.accountService.AccountBalance(defaultContext, request)

	// then:
	assert.Nil(suite.T(), actual)
	assert.NotNil(suite.T(), err)

	// when:
	allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

	// then:
	assert.Nil(suite.T(), allAccountBalancesResponse)
	assert.NotNil(suite.T(), err)
}

func (suite *accountServiceSuite) TestAccountBalanceThrowsWhenAddressInvalid() {
	for _, invalidAddress := range []string{"abc", "-1"} {
		suite.T().Run(invalidAddress, func(t *testing.T) {
			// given
			request := getAccountBalanceRequest(accountBalanceRequestUseAccount(invalidAddress))

			// when
			actual, err := suite.accountService.AccountBalance(defaultContext, request)

			// then
			assert.Equal(t, errors.ErrInvalidAccount, err)
			assert.Nil(t, actual)

			// when
			allAccountBalancesResponse, err := suite.accountService.AllAccountBalances(defaultContext, getAllAccountBalancesRequest(request))

			// then
			assert.Equal(t, errors.ErrInvalidAccount, err)
			assert.Nil(t, allAccountBalancesResponse)
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

func TestIsEd25519PublicKey(t *testing.T) {
	tests := []struct {
		publicKey []byte
		isEd25519 bool
	}{
		{
			publicKey: utils.MustMarshal(utils.Ed25519PublicKey()),
			isEd25519: true,
		},
		{
			publicKey: nil,
		},
		{
			publicKey: make([]byte, 0),
		},
		{
			publicKey: []byte{0x12},
		},
		{
			publicKey: []byte{0x12, 0x20, 0x33},
		},
		{
			publicKey: append([]byte{0x12, 0x21}, randstr.Bytes(32)...),
		},
		{
			publicKey: utils.MustMarshal(utils.EcdsaSecp256k1PublicKey()),
		},
		{
			publicKey: utils.MustMarshal(utils.KeyListKey([]*services.Key{utils.Ed25519PublicKey(), utils.EcdsaSecp256k1PublicKey()})),
		},
	}

	for _, tt := range tests {
		t.Run(hex.EncodeToString(tt.publicKey), func(t *testing.T) {
			assert.Equal(t, tt.isEd25519, isEd25519PublicKey(tt.publicKey))
		})
	}
}
