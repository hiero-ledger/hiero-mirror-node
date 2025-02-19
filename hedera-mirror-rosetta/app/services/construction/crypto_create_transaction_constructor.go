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
	"context"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type cryptoCreate struct {
	AutoRenewPeriod               int64            `json:"auto_renew_period"`
	InitialBalance                int64            `json:"-"`
	Key                           *types.PublicKey `json:"key" validate:"required"`
	MaxAutomaticTokenAssociations int32            `json:"max_automatic_token_associations"`
	Memo                          string           `json:"memo"`
	ProxyAccountId                *hiero.AccountID `json:"proxy_account_id"`
	// Add support for ReceiverSigRequired if needed and when the format to present an unknown account as a rosetta
	// AccountIdentifier id decided
}

type cryptoCreateTransactionConstructor struct {
	commonTransactionConstructor
}

func (c *cryptoCreateTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error) {
	cryptoCreate, payer, rErr := c.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	transaction := hiero.NewAccountCreateTransaction().
		SetInitialBalance(hiero.HbarFromTinybar(cryptoCreate.InitialBalance)).
		SetKey(cryptoCreate.Key.PublicKey)

	if cryptoCreate.AutoRenewPeriod > 0 {
		transaction.SetAutoRenewPeriod(time.Second * time.Duration(cryptoCreate.AutoRenewPeriod))
	}

	if cryptoCreate.MaxAutomaticTokenAssociations != 0 {
		transaction.SetMaxAutomaticTokenAssociations(cryptoCreate.MaxAutomaticTokenAssociations)
	}

	if cryptoCreate.Memo != "" {
		transaction.SetAccountMemo(cryptoCreate.Memo)
	}

	if cryptoCreate.ProxyAccountId != nil {
		transaction.SetProxyAccountID(*cryptoCreate.ProxyAccountId)
	}

	return transaction, []types.AccountId{*payer}, nil
}

func (c *cryptoCreateTransactionConstructor) Parse(_ context.Context, transaction hiero.TransactionInterface) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	cryptoCreateTransaction, ok := transaction.(*hiero.AccountCreateTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if cryptoCreateTransaction.GetTransactionID().AccountID == nil {
		log.Error("Transaction ID is not set")
		return nil, nil, errors.ErrInvalidTransaction
	}

	amount := types.HbarAmount{Value: -cryptoCreateTransaction.GetInitialBalance().AsTinybar()}
	payer, err := types.NewAccountIdFromSdkAccountId(*cryptoCreateTransaction.GetTransactionID().AccountID)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}
	metadata := make(map[string]interface{})
	operation := types.Operation{
		AccountId: payer,
		Amount:    &amount,
		Metadata:  metadata,
		Type:      c.GetOperationType(),
	}

	metadata["memo"] = cryptoCreateTransaction.GetAccountMemo()

	if cryptoCreateTransaction.GetAutoRenewPeriod() != 0 {
		metadata["auto_renew_period"] = int64(cryptoCreateTransaction.GetAutoRenewPeriod().Seconds())
	}

	if key, err := cryptoCreateTransaction.GetKey(); err != nil {
		log.Errorf("Failed to get key from crypto create transaction: %v", err)
		return nil, nil, errors.ErrInvalidTransaction
	} else if key == nil {
		log.Errorf("Key not set for the crypto create transaction")
		return nil, nil, errors.ErrInvalidTransaction
	} else {
		metadata["key"] = key.String()
	}

	if cryptoCreateTransaction.GetMaxAutomaticTokenAssociations() != 0 {
		metadata["max_automatic_token_associations"] = cryptoCreateTransaction.GetMaxAutomaticTokenAssociations()
	}

	if !isZeroAccountId(cryptoCreateTransaction.GetProxyAccountID()) {
		metadata["proxy_account_id"] = cryptoCreateTransaction.GetProxyAccountID().String()
	}

	return types.OperationSlice{operation}, []types.AccountId{payer}, nil
}

func (c *cryptoCreateTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	_, signer, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []types.AccountId{*signer}, nil
}

func (c *cryptoCreateTransactionConstructor) preprocess(operations types.OperationSlice) (
	*cryptoCreate,
	*types.AccountId,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, c.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	amount := operation.Amount
	if _, ok := amount.(*types.HbarAmount); !ok {
		log.Errorf("Operation amount currency is not HBAR: %v", operation.Amount)
		return nil, nil, errors.ErrInvalidCurrency
	} else if amount.GetValue() > 0 {
		log.Errorf("Initial transfer %d is > 0", amount.GetValue())
		return nil, nil, errors.ErrInvalidOperationsAmount
	}

	cryptoCreate := &cryptoCreate{}
	if rErr := parseOperationMetadata(c.validate, cryptoCreate, operation.Metadata); rErr != nil {
		log.Errorf("Failed to parse and validate operation metadata %v: %v", operation.Metadata, rErr)
		return nil, nil, rErr
	}

	cryptoCreate.InitialBalance = -amount.GetValue()

	return cryptoCreate, &operation.AccountId, nil
}

func newCryptoCreateTransactionConstructor() transactionConstructorWithType {
	return &cryptoCreateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hiero.NewAccountCreateTransaction(),
			types.OperationTypeCryptoCreateAccount,
		),
	}
}
