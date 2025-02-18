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
	"testing"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func dummyGenesisBlock() *types.Block {
	return &types.Block{
		Index:               1,
		Hash:                "0x123jsjs",
		ConsensusStartNanos: 1000000,
		ConsensusEndNanos:   20000000,
		ParentIndex:         0,
		ParentHash:          "",
	}
}

func dummySecondLatestBlock() *types.Block {
	return &types.Block{
		Index:               2,
		Hash:                "0x1323jsjs",
		ConsensusStartNanos: 40000000,
		ConsensusEndNanos:   70000000,
		ParentIndex:         1,
		ParentHash:          "0x123jsjs",
	}
}

func getNetworkAPIService(abr interfaces.AddressBookEntryRepository, base BaseService) server.NetworkAPIServicer {
	network := &rTypes.NetworkIdentifier{
		Blockchain: "SomeBlockchain",
		Network:    "SomeNetwork",
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network:  "SomeSubNetwork",
			Metadata: nil,
		},
	}
	version := &rTypes.Version{
		RosettaVersion:    "1",
		NodeVersion:       "1",
		MiddlewareVersion: nil,
		Metadata:          nil,
	}

	return NewNetworkAPIService(base, abr, network, version)
}

func TestOfflineNetworkServiceSuite(t *testing.T) {
	suite.Run(t, new(offlineNetworkServiceSuite))
}

type offlineNetworkServiceSuite struct {
	suite.Suite
	networkService server.NetworkAPIServicer
	operationTypes []string
}

func (suite *offlineNetworkServiceSuite) SetupSuite() {
	suite.operationTypes = tools.GetStringValuesFromInt32StringMap(types.GetTransactionTypes())
	suite.operationTypes = append(suite.operationTypes, types.OperationTypeFee)
}

func (suite *offlineNetworkServiceSuite) BeforeTest(_, _ string) {
	suite.networkService = getNetworkAPIService(nil, NewOfflineBaseService())
}

func (suite *offlineNetworkServiceSuite) TestNetworkList() {
	// given:
	expectedResult := &rTypes.NetworkListResponse{
		NetworkIdentifiers: []*rTypes.NetworkIdentifier{
			{
				Blockchain: "SomeBlockchain",
				Network:    "SomeNetwork",
				SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
					Network:  "SomeSubNetwork",
					Metadata: nil,
				},
			},
		},
	}

	// when:
	res, e := suite.networkService.NetworkList(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult, res)
	assert.Nil(suite.T(), e)
}

func (suite *offlineNetworkServiceSuite) TestNetworkOptions() {
	// given:
	expectedErrors := []*rTypes.Error{
		errors.ErrAccountNotFound,
		errors.ErrBlockNotFound,
		errors.ErrInvalidAccount,
		errors.ErrInvalidAmount,
		errors.ErrInvalidOperationsAmount,
		errors.ErrInvalidOperationsTotalAmount,
		errors.ErrInvalidPublicKey,
		errors.ErrInvalidSignatureVerification,
		errors.ErrInvalidTransactionIdentifier,
		errors.ErrMultipleOperationTypesPresent,
		errors.ErrNodeIsStarting,
		errors.ErrNotImplemented,
		errors.ErrOperationResultsNotFound,
		errors.ErrOperationTypesNotFound,
		errors.ErrStartMustNotBeAfterEnd,
		errors.ErrTransactionDecodeFailed,
		errors.ErrTransactionMarshallingFailed,
		errors.ErrTransactionUnmarshallingFailed,
		errors.ErrTransactionSubmissionFailed,
		errors.ErrTransactionNotFound,
		errors.ErrEmptyOperations,
		errors.ErrTransactionInvalidType,
		errors.ErrTransactionHashFailed,
		errors.ErrTransactionFreezeFailed,
		errors.ErrInvalidArgument,
		errors.ErrDatabaseError,
		errors.ErrInvalidOperationMetadata,
		errors.ErrOperationTypeUnsupported,
		errors.ErrInvalidOperationType,
		errors.ErrNoSignature,
		errors.ErrInvalidOperations,
		errors.ErrInvalidToken,
		errors.ErrTokenNotFound,
		errors.ErrInvalidTransaction,
		errors.ErrInvalidCurrency,
		errors.ErrInvalidSignatureType,
		errors.ErrEndpointNotSupportedInOfflineMode,
		errors.ErrInvalidCurveType,
		errors.ErrInvalidOptions,
		errors.ErrInvalidTransactionMemo,
		errors.ErrNodeAccountIdsEmpty,
		errors.ErrMissingNodeAccountIdMetadata,
		errors.ErrInternalServerError,
	}

	expectedResult := &rTypes.NetworkOptionsResponse{
		Version: &rTypes.Version{
			RosettaVersion:    "1",
			NodeVersion:       "1",
			MiddlewareVersion: nil,
			Metadata:          nil,
		},
		Allow: &rTypes.Allow{
			OperationStatuses: []*rTypes.OperationStatus{
				{
					Status:     "SUCCESS",
					Successful: true,
				},
				{
					Status:     "OK",
					Successful: false,
				},
				{
					Status:     "GENERAL_ERROR",
					Successful: false,
				},
			},
			OperationTypes:          suite.operationTypes,
			Errors:                  expectedErrors,
			HistoricalBalanceLookup: true,
		},
	}

	// when:
	res, e := suite.networkService.NetworkOptions(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult.Version, res.Version)
	assert.Equal(suite.T(), expectedResult.Allow.HistoricalBalanceLookup, res.Allow.HistoricalBalanceLookup)
	assert.Subset(suite.T(), res.Allow.OperationStatuses, expectedResult.Allow.OperationStatuses)
	assert.ElementsMatch(suite.T(), expectedResult.Allow.OperationTypes, res.Allow.OperationTypes)
	assert.ElementsMatch(suite.T(), expectedResult.Allow.Errors, res.Allow.Errors)
	assert.Nil(suite.T(), e)
}

func (suite *offlineNetworkServiceSuite) TestNetworkStatus() {
	// given
	// when
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then
	assert.Nil(suite.T(), res)
	assert.Equal(suite.T(), errors.ErrEndpointNotSupportedInOfflineMode, e)
}

func TestOnlineNetworkServiceSuite(t *testing.T) {
	suite.Run(t, new(onlineNetworkServiceSuite))
}

type onlineNetworkServiceSuite struct {
	offlineNetworkServiceSuite
	mockAddressBookEntryRepo *mocks.MockAddressBookEntryRepository
	mockBlockRepo            *mocks.MockBlockRepository
	mockTransactionRepo      *mocks.MockTransactionRepository
}

func (suite *onlineNetworkServiceSuite) BeforeTest(_, _ string) {
	suite.mockAddressBookEntryRepo = &mocks.MockAddressBookEntryRepository{}
	suite.mockBlockRepo = &mocks.MockBlockRepository{}
	suite.mockTransactionRepo = &mocks.MockTransactionRepository{}

	baseService := NewOnlineBaseService(suite.mockBlockRepo, suite.mockTransactionRepo)
	suite.networkService = getNetworkAPIService(suite.mockAddressBookEntryRepo, baseService)
}

func (suite *onlineNetworkServiceSuite) TestNetworkStatus() {
	// given:
	exampleEntries := &types.AddressBookEntries{Entries: []types.AddressBookEntry{}}

	expectedResult := &rTypes.NetworkStatusResponse{
		CurrentBlockIdentifier: &rTypes.BlockIdentifier{
			Index: 2,
			Hash:  "0x1323jsjs",
		},
		CurrentBlockTimestamp: 40,
		GenesisBlockIdentifier: &rTypes.BlockIdentifier{
			Index: 1,
			Hash:  "0x123jsjs",
		},
		Peers: []*rTypes.Peer{},
	}

	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), mocks.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(dummySecondLatestBlock(), mocks.NilError)
	suite.mockAddressBookEntryRepo.On("Entries").Return(exampleEntries, mocks.NilError)

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Equal(suite.T(), expectedResult, res)
	assert.Nil(suite.T(), e)
}

func (suite *onlineNetworkServiceSuite) TestNetworkStatusThrowsWhenRetrieveGenesisFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *onlineNetworkServiceSuite) TestNetworkStatusThrowsWhenRetrieveSecondLatestFails() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), mocks.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(mocks.NilBlock, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}

func (suite *onlineNetworkServiceSuite) TestNetworkStatusThrowsWhenEntriesFail() {
	// given:
	suite.mockBlockRepo.On("RetrieveGenesis").Return(dummyGenesisBlock(), mocks.NilError)
	suite.mockBlockRepo.On("RetrieveLatest").Return(dummySecondLatestBlock(), mocks.NilError)
	suite.mockAddressBookEntryRepo.On("Entries").Return(mocks.NilEntries, &rTypes.Error{})

	// when:
	res, e := suite.networkService.NetworkStatus(nil, nil)

	// then:
	assert.Nil(suite.T(), res)
	assert.NotNil(suite.T(), e)
}
