// SPDX-License-Identifier: Apache-2.0

package construction

import (
	"context"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
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
		SetKeyWithoutAlias(cryptoCreate.Key.PublicKey)

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
	cryptoCreateTransaction, ok := transaction.(hiero.AccountCreateTransaction)
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
	metadata := make(map[string]any)
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

	if rErr := validateSupportedAccountCreateFields(cryptoCreateTransaction); rErr != nil {
		return nil, nil, rErr
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

// Fields this API already returns from an account create. Any other populated protobuf field is rejected.
var allowedCryptoCreateFields = map[protoreflect.Name]struct{}{
	"autoRenewPeriod":                  {},
	"initialBalance":                   {},
	"key":                              {},
	"max_automatic_token_associations": {},
	"memo":                             {},
	"proxyAccountID":                   {},
}

func validateSupportedAccountCreateFields(tx hiero.AccountCreateTransaction) *rTypes.Error {
	// A frozen transaction has signed body bytes, so every populated protobuf field can be checked
	// against the allowlist. An unfrozen transaction has no body yet; only the public SDK getters
	// can be checked, and they do not expose fields such as shard, realm, or delegation address.
	if tx.IsFrozen() {
		return validateFrozenAccountCreateFields(tx)
	}

	if field := unsupportedAccountCreateSDKField(tx); field != "" {
		log.Errorf("Account create field %s is not supported", field)
		return errors.ErrInvalidTransaction
	}

	return nil
}

func validateFrozenAccountCreateFields(tx hiero.AccountCreateTransaction) *rTypes.Error {
	body := &services.TransactionBody{}
	if err := proto.Unmarshal(tx.GetSignedTransactionBodyBytes(0), body); err != nil {
		log.Errorf("Failed to read the signed account create body: %s", err)
		return errors.ErrInvalidTransaction
	}

	create := body.GetCryptoCreateAccount()
	if create == nil {
		log.Error("Stored account create transaction body is not a crypto create")
		return errors.ErrInvalidTransaction
	}

	if field, ok := unsupportedCryptoCreateField(create); ok {
		log.Errorf("Account create field %s is not supported", field)
		return errors.ErrInvalidTransaction
	}

	return nil
}

func unsupportedCryptoCreateField(body *services.CryptoCreateTransactionBody) (string, bool) {
	var unsupported string
	body.ProtoReflect().Range(func(field protoreflect.FieldDescriptor, _ protoreflect.Value) bool {
		if _, allowed := allowedCryptoCreateFields[field.Name()]; !allowed {
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

	return "", false
}

func unsupportedAccountCreateSDKField(tx hiero.AccountCreateTransaction) string {
	if len(tx.GetAlias()) > 0 {
		return "alias"
	}
	if !isZeroAccountId(tx.GetStakedAccountID()) {
		return "staked_account_id"
	}
	if tx.GetStakedNodeID() != 0 {
		return "staked_node_id"
	}
	if tx.GetDeclineStakingReward() {
		return "decline_reward"
	}
	if tx.GetReceiverSignatureRequired() {
		return "receiver_sig_required"
	}
	if len(tx.GetHooks()) > 0 {
		return "hook_creation_details"
	}

	return ""
}

func newCryptoCreateTransactionConstructor() transactionConstructorWithType {
	return &cryptoCreateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hiero.NewAccountCreateTransaction(),
			types.OperationTypeCryptoCreateAccount,
		),
	}
}
