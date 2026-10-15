// SPDX-License-Identifier: Apache-2.0

package construction

import (
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/domain"
	sdkpb "github.com/hiero-ledger/hiero-sdk-go/v2/proto/sdk"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"google.golang.org/protobuf/proto"
)

const (
	initialBalance                      = 10
	maxAutomaticTokenAssociations int32 = 10
)

var (
	_, newAccountPublicKey = domain.GenEd25519KeyPair()
	proxyAccountId         = hiero.AccountID{Account: 6000}
)

type updateOperationsFunc func(types.OperationSlice) types.OperationSlice

func addOperation(operations types.OperationSlice) types.OperationSlice {
	return append(operations, types.Operation{})
}

func getEmptyOperations(types.OperationSlice) types.OperationSlice {
	return make(types.OperationSlice, 0)
}

func getEmptyOperationMetadata(operations types.OperationSlice) types.OperationSlice {
	for index := range operations {
		operation := &operations[index]
		operation.Metadata = nil
	}
	return operations
}

func deleteOperationMetadata(key string) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			delete(operation.Metadata, key)
		}
		return operations
	}
}

func negateAmountValue(operations types.OperationSlice) types.OperationSlice {
	for index := range operations {
		operation := &operations[index]
		amount := operation.Amount
		switch a := amount.(type) {
		case *types.HbarAmount:
			a.Value = -a.Value
		}
	}
	return operations
}

func updateOperationMetadata(key string, value any) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			operation.Metadata[key] = value
		}
		return operations
	}
}

func updateOperationType(operationType string) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			operation.Type = operationType
		}
		return operations
	}
}

func TestCryptoCreateTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoCreateTransactionConstructorSuite))
}

type cryptoCreateTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoCreateTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoCreateTransactionConstructor(true)
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newCryptoCreateTransactionConstructor(true)
	assert.Equal(suite.T(), types.HbarAmount{Value: 5_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoCreateTransactionConstructor(true)
	assert.Equal(suite.T(), types.OperationTypeCryptoCreateAccount, h.GetOperationType())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoCreateTransactionConstructor(true)
	assert.Equal(suite.T(), "AccountCreateTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getCryptoCreateOperations()
			h := newCryptoCreateTransactionConstructor(true)

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			tx, signers, err := h.Construct(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				assertCryptoCreateTransaction(t, operations[0], tx)
			}
		})
	}
}

func (suite *cryptoCreateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() hiero.TransactionInterface {
		return *hiero.NewAccountCreateTransaction().
			SetAccountMemo(memo).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
			SetKeyWithoutAlias(newAccountPublicKey).
			SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
			SetProxyAccountID(proxyAccountId).
			SetTransactionID(hiero.TransactionIDGenerate(sdkAccountIdA))
	}

	var tests = []struct {
		name                string
		getTransaction      func() hiero.TransactionInterface
		expectError         bool
		skipFieldValidation bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name:           "Alias",
			getTransaction: accountCreateWith(func(tx *hiero.AccountCreateTransaction) { tx.SetAlias(aliasEvmAddress) }),
			expectError:    true,
		},
		{
			name:                "AliasIgnoredWhenValidationDisabled",
			getTransaction:      accountCreateWith(func(tx *hiero.AccountCreateTransaction) { tx.SetAlias(aliasEvmAddress) }),
			skipFieldValidation: true,
		},
		{
			name:           "StakedAccountID",
			getTransaction: accountCreateWith(func(tx *hiero.AccountCreateTransaction) { tx.SetStakedAccountID(proxyAccountId) }),
			expectError:    true,
		},
		{
			name:           "StakedNodeID",
			getTransaction: accountCreateWith(func(tx *hiero.AccountCreateTransaction) { tx.SetStakedNodeID(1) }),
			expectError:    true,
		},
		{
			name:           "DeclineReward",
			getTransaction: accountCreateWith(func(tx *hiero.AccountCreateTransaction) { tx.SetDeclineStakingReward(true) }),
			expectError:    true,
		},
		{
			name: "ReceiverSignatureRequired",
			getTransaction: accountCreateWith(func(tx *hiero.AccountCreateTransaction) {
				tx.SetReceiverSignatureRequired(true)
			}),
			expectError: true,
		},
		{
			name: "KeyNotSet",
			getTransaction: func() hiero.TransactionInterface {
				tx := defaultGetTransaction()
				accountCreateTx, _ := tx.(hiero.AccountCreateTransaction)
				accountCreateTx.SetKeyWithoutAlias(nil)
				return accountCreateTx
			},
			expectError: true,
		},
		{
			name: "OutOfRangePayerAccountId",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewAccountCreateTransaction().
					SetAccountMemo(memo).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
					SetKeyWithoutAlias(newAccountPublicKey).
					SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
					SetProxyAccountID(proxyAccountId).
					SetTransactionID(hiero.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() hiero.TransactionInterface {
				return hiero.NewAccountCreateTransaction().
					SetAccountMemo(memo).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
					SetKeyWithoutAlias(newAccountPublicKey).
					SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
					SetProxyAccountID(proxyAccountId)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getCryptoCreateOperations()

			h := newCryptoCreateTransactionConstructor(!tt.skipFieldValidation)
			tx := tt.getTransaction()

			// when
			operations, signers, err := h.Parse(defaultContext, tx)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, operations)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				assert.ElementsMatch(t, expectedOperations, operations)
			}
		})
	}
}

func (suite *cryptoCreateTransactionConstructorSuite) TestParseStakedNodeIDZeroFromBytes() {
	tx := accountCreateFromBytes(suite.T(), func(body *services.CryptoCreateTransactionBody) {
		body.StakedId = &services.CryptoCreateTransactionBody_StakedNodeId{StakedNodeId: 0}
	})

	operations, signers, err := newCryptoCreateTransactionConstructor(true).Parse(defaultContext, tx)

	assert.Equal(suite.T(), errors.ErrInvalidTransaction, err)
	assert.Nil(suite.T(), operations)
	assert.Nil(suite.T(), signers)
}

func (suite *cryptoCreateTransactionConstructorSuite) TestParseRoundTripWithoutHiddenFields() {
	tx, err := hiero.NewAccountCreateTransaction().
		SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
		SetKeyWithoutAlias(newAccountPublicKey).
		SetTransactionID(hiero.TransactionIDGenerate(sdkAccountIdA)).
		SetNodeAccountIDs([]hiero.AccountID{{Account: 3}}).
		Freeze()
	suite.Require().NoError(err)

	raw, err := hiero.TransactionToBytes(tx)
	suite.Require().NoError(err)

	parsed, err := hiero.TransactionFromBytes(raw)
	suite.Require().NoError(err)

	operations, signers, rErr := newCryptoCreateTransactionConstructor(true).Parse(defaultContext, parsed)

	assert.Nil(suite.T(), rErr)
	assert.ElementsMatch(suite.T(), []types.AccountId{accountIdA}, signers)
	require.Len(suite.T(), operations, 1)

	accountCreate := parsed.(hiero.AccountCreateTransaction)
	assert.Equal(suite.T(), newAccountPublicKey.String(), operations[0].Metadata["key"])
	assert.Equal(suite.T(), accountCreate.GetAccountMemo(), operations[0].Metadata["memo"])
	assert.Equal(suite.T(), int64(accountCreate.GetAutoRenewPeriod().Seconds()), operations[0].Metadata["auto_renew_period"])
}

func (suite *cryptoCreateTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidMetadataKey",
			updateOperations: updateOperationMetadata("key", "key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataAutoRenewPeriod",
			updateOperations: updateOperationMetadata("auto_renew_period", "x"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataMemo",
			updateOperations: updateOperationMetadata("memo", 156),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataProxyAccountId",
			updateOperations: updateOperationMetadata("proxy_account_id", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "MissingMetadata",
			updateOperations: getEmptyOperationMetadata,
			expectError:      true,
		},
		{
			name:             "MissingMetadataKey",
			updateOperations: deleteOperationMetadata("key"),
			expectError:      true,
		},
		{
			name:             "MultipleOperations",
			updateOperations: addOperation,
			expectError:      true,
		},
		{
			name:             "NegativeInitialBalance",
			updateOperations: negateAmountValue,
			expectError:      true,
		},
		{
			name:             "InvalidOperationType",
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getCryptoCreateOperations()
			h := newCryptoCreateTransactionConstructor(true)

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			signers, err := h.Preprocess(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
			}
		})
	}
}

func assertCryptoCreateTransaction(
	t *testing.T,
	operation types.Operation,
	actual hiero.TransactionInterface,
) {
	assert.IsType(t, &hiero.AccountCreateTransaction{}, actual)

	tx, _ := actual.(*hiero.AccountCreateTransaction)
	assert.False(t, tx.IsFrozen())
	key, err := tx.GetKey()
	assert.NoError(t, err)
	assert.Equal(t, operation.Metadata["key"], key.String())
	assert.Equal(t, operation.Metadata["auto_renew_period"], int64(tx.GetAutoRenewPeriod().Seconds()))
	assert.Equal(t, operation.Metadata["max_automatic_token_associations"], tx.GetMaxAutomaticTokenAssociations())
	assert.Equal(t, operation.Metadata["memo"], tx.GetAccountMemo())
	assert.Equal(t, operation.Metadata["proxy_account_id"], tx.GetProxyAccountID().String())
}

const aliasEvmAddress = "0000000000000000000000000000000000000001"

func accountCreateWith(update func(*hiero.AccountCreateTransaction)) func() hiero.TransactionInterface {
	return func() hiero.TransactionInterface {
		tx := hiero.NewAccountCreateTransaction().
			SetAccountMemo(memo).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
			SetKeyWithoutAlias(newAccountPublicKey).
			SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
			SetProxyAccountID(proxyAccountId).
			SetTransactionID(hiero.TransactionIDGenerate(sdkAccountIdA))
		update(tx)
		return *tx
	}
}

func accountCreateFromBytes(
	t *testing.T,
	mutate func(*services.CryptoCreateTransactionBody),
) hiero.TransactionInterface {
	t.Helper()

	frozen, err := hiero.NewAccountCreateTransaction().
		SetInitialBalance(hiero.HbarFromTinybar(initialBalance)).
		SetKeyWithoutAlias(newAccountPublicKey).
		SetTransactionID(hiero.TransactionIDGenerate(sdkAccountIdA)).
		SetNodeAccountIDs([]hiero.AccountID{{Account: 3}}).
		Freeze()
	require.NoError(t, err)

	raw, err := hiero.TransactionToBytes(frozen)
	require.NoError(t, err)

	list := sdkpb.TransactionList{}
	require.NoError(t, proto.Unmarshal(raw, &list))
	require.NotEmpty(t, list.TransactionList)

	signed := &services.SignedTransaction{}
	require.NoError(t, proto.Unmarshal(list.TransactionList[0].GetSignedTransactionBytes(), signed))
	body := &services.TransactionBody{}
	require.NoError(t, proto.Unmarshal(signed.GetBodyBytes(), body))
	require.NotNil(t, body.GetCryptoCreateAccount())
	mutate(body.GetCryptoCreateAccount())

	updated, err := proto.Marshal(body)
	require.NoError(t, err)
	signed.BodyBytes = updated
	signedBytes, err := proto.Marshal(signed)
	require.NoError(t, err)
	list.TransactionList[0].SignedTransactionBytes = signedBytes

	raw, err = proto.Marshal(&list)
	require.NoError(t, err)
	tx, err := hiero.TransactionFromBytes(raw)
	require.NoError(t, err)
	return tx
}

func getCryptoCreateOperations() types.OperationSlice {
	operation := types.Operation{
		AccountId: accountIdA,
		Amount:    &types.HbarAmount{Value: -initialBalance},
		Type:      types.OperationTypeCryptoCreateAccount,
		Metadata: map[string]any{
			"auto_renew_period":                autoRenewPeriod,
			"key":                              newAccountPublicKey.String(),
			"max_automatic_token_associations": maxAutomaticTokenAssociations,
			"memo":                             memo,
			"proxy_account_id":                 proxyAccountId.String(),
		},
	}

	return types.OperationSlice{operation}
}
