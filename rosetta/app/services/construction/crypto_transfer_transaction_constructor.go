// SPDX-License-Identifier: Apache-2.0

package construction

import (
	"context"
	"math"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
)

// maxTinybarTransfer is the maximum HBAR supply expressed in tinybars.
const maxTinybarTransfer int64 = 5_000_000_000_000_000_000

type cryptoTransferTransactionConstructor struct {
	commonTransactionConstructor
	validateCryptoTransferFields bool
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
	transferTransaction, ok := transaction.(hiero.TransferTransaction)
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

	if c.validateCryptoTransferFields {
		if rErr := validateSupportedCryptoTransferFields(transferTransaction); rErr != nil {
			return nil, nil, rErr
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
	var totalAmount int64
	transfers := make([]transfer, 0, len(operations))

	for _, operation := range operations {
		accountId := operation.AccountId
		amount := operation.Amount
		symbol := amount.GetSymbol()
		value := amount.GetValue()
		if symbol != types.CurrencyHbar.Symbol {
			return nil, nil, errors.ErrInvalidCurrency
		}
		if value == 0 || value < -maxTinybarTransfer || value > maxTinybarTransfer {
			return nil, nil, errors.ErrInvalidOperationsAmount
		}

		var ok bool
		if totalAmount, ok = addInt64(totalAmount, value); !ok {
			return nil, nil, errors.ErrInvalidOperationsAmount
		}

		sdkAccountId, err := accountId.ToSdkAccountId()
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}
		transfers = append(transfers, transfer{account: sdkAccountId, amount: amount})

		if value < 0 {
			senderMap[accountId.String()] = accountId
		}
	}

	if totalAmount != 0 {
		log.Errorf("Transfer sum for symbol %s is not 0", types.CurrencyHbar.Symbol)
		return nil, nil, errors.ErrInvalidOperationsTotalAmount
	}

	return transfers, senderMap.toSenders(), nil
}

func addInt64(left, right int64) (int64, bool) {
	if (right > 0 && left > math.MaxInt64-right) || (right < 0 && left < math.MinInt64-right) {
		return 0, false
	}

	return left + right, true
}

func newCryptoTransferTransactionConstructor(validateCryptoTransferFields bool) transactionConstructorWithType {
	return &cryptoTransferTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hiero.NewTransferTransaction(),
			types.OperationTypeCryptoTransfer,
		),
		validateCryptoTransferFields: validateCryptoTransferFields,
	}
}

// Fields this API already returns from a crypto transfer. Any other populated protobuf field is rejected.
var allowedCryptoTransferFields = map[protoreflect.Name]struct{}{
	"transfers": {},
}

var allowedAccountAmountFields = map[protoreflect.Name]struct{}{
	"accountID": {},
	"amount":    {},
}

func validateSupportedCryptoTransferFields(tx hiero.TransferTransaction) *rTypes.Error {
	// A frozen transaction has signed body bytes, so every populated protobuf field can be checked
	// against the allowlist. An unfrozen transaction has no body yet; only the public SDK getters
	// can be checked, and they do not expose fields such as an HBAR approval or hook call.
	if tx.IsFrozen() {
		return validateFrozenCryptoTransferFields(tx)
	}

	if field := unsupportedCryptoTransferSDKField(tx); field != "" {
		log.Errorf("Crypto transfer field %s is not supported", field)
		return errors.ErrInvalidTransaction
	}

	return nil
}

func validateFrozenCryptoTransferFields(tx hiero.TransferTransaction) *rTypes.Error {
	body := &services.TransactionBody{}
	if err := proto.Unmarshal(tx.GetSignedTransactionBodyBytes(0), body); err != nil {
		log.Errorf("Failed to read the signed crypto transfer body: %s", err)
		return errors.ErrInvalidTransaction
	}

	transfer := body.GetCryptoTransfer()
	if transfer == nil {
		log.Error("Stored transfer transaction body is not a crypto transfer")
		return errors.ErrInvalidTransaction
	}

	if field, ok := unsupportedCryptoTransferField(transfer); ok {
		log.Errorf("Crypto transfer field %s is not supported", field)
		return errors.ErrInvalidTransaction
	}

	return nil
}

func unsupportedCryptoTransferField(body *services.CryptoTransferTransactionBody) (string, bool) {
	var unsupported string
	body.ProtoReflect().Range(func(field protoreflect.FieldDescriptor, _ protoreflect.Value) bool {
		if _, allowed := allowedCryptoTransferFields[field.Name()]; !allowed {
			unsupported = string(field.Name())
			return false
		}
		return true
	})
	if unsupported != "" {
		return unsupported, true
	}
	if len(body.ProtoReflect().GetUnknown()) > 0 {
		return "unknown", true
	}

	for _, amount := range body.GetTransfers().GetAccountAmounts() {
		if field, ok := unsupportedAccountAmountField(amount); ok {
			return field, true
		}
	}

	return "", false
}

func unsupportedAccountAmountField(amount *services.AccountAmount) (string, bool) {
	if amount == nil {
		return "", false
	}

	var unsupported string
	amount.ProtoReflect().Range(func(field protoreflect.FieldDescriptor, _ protoreflect.Value) bool {
		if _, allowed := allowedAccountAmountFields[field.Name()]; !allowed {
			unsupported = string(field.Name())
			return false
		}
		return true
	})
	if unsupported != "" {
		return unsupported, true
	}
	if len(amount.ProtoReflect().GetUnknown()) > 0 {
		return "unknown", true
	}

	return "", false
}

func unsupportedCryptoTransferSDKField(tx hiero.TransferTransaction) string {
	if len(tx.GetTokenTransfers()) > 0 {
		return "tokenTransfers"
	}
	if len(tx.GetNftTransfers()) > 0 {
		return "nftTransfers"
	}

	return ""
}

// RejectUnsupportedCryptoTransferFields rejects a transfer whose body contains fields this API does not return.
func RejectUnsupportedCryptoTransferFields(transaction hiero.TransactionInterface) *rTypes.Error {
	transferTransaction, ok := transaction.(hiero.TransferTransaction)
	if !ok {
		return nil
	}

	return validateSupportedCryptoTransferFields(transferTransaction)
}
