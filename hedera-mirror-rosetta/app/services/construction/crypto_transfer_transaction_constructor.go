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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type cryptoTransferTransactionConstructor struct {
	commonTransactionConstructor
}

type transfer struct {
	account hiero.AccountID
	amount  types.Amount
}

type senderMap map[string]types.AccountId

func (m senderMap) toSenders() []types.AccountId {
	senders := make([]types.AccountId, 0, len(m))
	for _, sender := range m {
		senders = append(senders, sender)
	}
	return senders
}

func (c *cryptoTransferTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error) {
	transfers, senders, rErr := c.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	transaction := hiero.NewTransferTransaction()

	for _, transfer := range transfers {
		switch amount := transfer.amount.(type) {
		case *types.HbarAmount:
			transaction.AddHbarTransfer(transfer.account, hiero.HbarFromTinybar(amount.Value))
		}
	}

	return transaction, senders, nil
}

func (c *cryptoTransferTransactionConstructor) Parse(_ context.Context, transaction hiero.TransactionInterface) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	transferTransaction, ok := transaction.(*hiero.TransferTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if transferTransaction.GetTransactionID().AccountID == nil {
		return nil, nil, errors.ErrInvalidTransaction
	}

	hbarTransferMap := transferTransaction.GetHbarTransfers()
	numOperations := len(hbarTransferMap)
	operations := make(types.OperationSlice, 0, numOperations)

	for accountId, hbarAmount := range hbarTransferMap {
		var err *rTypes.Error
		amount := &types.HbarAmount{Value: hbarAmount.AsTinybar()}
		if operations, err = c.addOperation(accountId, amount, operations); err != nil {
			return nil, nil, err
		}
	}

	senderMap := senderMap{}
	for _, operation := range operations {
		if operation.Amount.GetValue() < 0 {
			senderMap[operation.AccountId.String()] = operation.AccountId
		}
	}

	return operations, senderMap.toSenders(), nil
}

func (c *cryptoTransferTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	_, senders, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return senders, nil
}

func (c *cryptoTransferTransactionConstructor) addOperation(
	sdkAccountId hiero.AccountID,
	amount types.Amount,
	operations types.OperationSlice,
) (types.OperationSlice, *rTypes.Error) {
	accountId, err := types.NewAccountIdFromSdkAccountId(sdkAccountId)
	if err != nil {
		return nil, errors.ErrInvalidAccount
	}
	operation := types.Operation{
		Index:     int64(len(operations)),
		Type:      c.GetOperationType(),
		AccountId: accountId,
		Amount:    amount,
	}
	return append(operations, operation), nil
}

func (c *cryptoTransferTransactionConstructor) preprocess(operations types.OperationSlice) (
	[]transfer,
	[]types.AccountId,
	*rTypes.Error,
) {
	if err := validateOperations(operations, 0, c.GetOperationType(), false); err != nil {
		return nil, nil, err
	}

	senderMap := senderMap{}
	totalAmounts := make(map[string]int64)
	transfers := make([]transfer, 0, len(operations))

	for _, operation := range operations {
		accountId := operation.AccountId
		amount := operation.Amount
		if amount.GetValue() == 0 {
			return nil, nil, errors.ErrInvalidOperationsAmount
		}

		sdkAccountId, err := accountId.ToSdkAccountId()
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}
		transfers = append(transfers, transfer{account: sdkAccountId, amount: amount})

		if amount.GetValue() < 0 {
			senderMap[accountId.String()] = accountId
		}

		totalAmounts[amount.GetSymbol()] += amount.GetValue()
	}

	for symbol, totalAmount := range totalAmounts {
		if totalAmount != 0 {
			log.Errorf("Transfer sum for symbol %s is not 0", symbol)
			return nil, nil, errors.ErrInvalidOperationsTotalAmount
		}
	}

	return transfers, senderMap.toSenders(), nil
}

func newCryptoTransferTransactionConstructor() transactionConstructorWithType {
	return &cryptoTransferTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hiero.NewTransferTransaction(),
			types.OperationTypeCryptoTransfer,
		),
	}
}
