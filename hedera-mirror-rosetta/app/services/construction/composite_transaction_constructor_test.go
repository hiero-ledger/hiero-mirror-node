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

package construction

import (
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	cryptoTransferTransaction = hiero.NewTransferTransaction()
	tokenCreateTransaction    = hiero.NewTokenCreateTransaction()
	cryptoTransferOperations  = types.OperationSlice{{Type: types.OperationTypeCryptoTransfer}}
	mixedOperations           = types.OperationSlice{
		{Type: types.OperationTypeCryptoTransfer},
		{Type: types.OperationTypeTokenCreate},
	}
	unsupportedOperations = types.OperationSlice{{Type: types.OperationTypeTokenCreate}}
	signers               = []types.AccountId{types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(150))}
)

func TestCompositeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(compositeTransactionConstructorSuite))
}

type compositeTransactionConstructorSuite struct {
	suite.Suite
	constructor     TransactionConstructor
	mockConstructor *mocks.MockTransactionConstructorWithType
}

func (suite *compositeTransactionConstructorSuite) SetupTest() {
	mockConstructor := &mocks.MockTransactionConstructorWithType{}
	constructor := &compositeTransactionConstructor{
		constructorsByOperationType:   map[string]transactionConstructorWithType{},
		constructorsByTransactionType: map[string]transactionConstructorWithType{},
	}
	constructor.addConstructor(mockConstructor)

	suite.constructor = constructor
	suite.mockConstructor = mockConstructor
}

func (suite *compositeTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := NewTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *compositeTransactionConstructorSuite) TestConstruct() {
	// given
	suite.mockConstructor.
		On("Construct", defaultContext, cryptoTransferOperations).
		Return(cryptoTransferTransaction, signers, mocks.NilError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(defaultContext, cryptoTransferOperations)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferTransaction, actualTx)
	assert.Equal(suite.T(), signers, actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructFail() {
	// given
	suite.mockConstructor.
		On("Construct", defaultContext, cryptoTransferOperations).
		Return(mocks.NilHederaTransaction, mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(defaultContext, cryptoTransferOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructEmptyOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(defaultContext, types.OperationSlice{})

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructUnsupportedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(defaultContext, unsupportedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructMixedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(defaultContext, mixedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	// given
	expected := types.HbarAmount{Value: 1000}
	suite.mockConstructor.On("GetDefaultMaxTransactionFee").Return(expected)

	// when
	actual, err := suite.constructor.GetDefaultMaxTransactionFee(types.OperationTypeCryptoTransfer)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestGetDefaultMaxTransactionFeeInvalidOperationType() {
	// given
	// when
	actual, err := suite.constructor.GetDefaultMaxTransactionFee("invalidOperationType")

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.HbarAmount{}, actual)
	suite.mockConstructor.AssertNotCalled(suite.T(), "GetDefaultMaxTransactionFee")
}

func (suite *compositeTransactionConstructorSuite) TestParse() {
	// given
	suite.mockConstructor.
		On("Parse", defaultContext, cryptoTransferTransaction).
		Return(cryptoTransferOperations, signers, mocks.NilError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, cryptoTransferTransaction)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferOperations, actualOperations)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseFail() {
	// given
	suite.mockConstructor.
		On("Parse", defaultContext, cryptoTransferTransaction).
		Return(mocks.NilOperations, mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, cryptoTransferTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseUnsupportedTransaction() {
	// given

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, tokenCreateTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocess() {
	// given
	suite.mockConstructor.
		On("Preprocess", defaultContext, cryptoTransferOperations).
		Return(signers, mocks.NilError)

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, cryptoTransferOperations)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessFail() {
	// given
	suite.mockConstructor.
		On("Preprocess", defaultContext, cryptoTransferOperations).
		Return(mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, cryptoTransferOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessUnsupportedOperations() {
	// given

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, unsupportedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}
