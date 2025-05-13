// SPDX-License-Identifier: Apache-2.0

package types

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"strings"
)

const (
	generalErrorStatus     = "GENERAL_ERROR"
	MaxTransactionResult   = int32(337)
	MaxTransactionType     = int32(57)
	MetadataKeyMemo        = "memo"
	unknownTransactionType = "UNKNOWN"

	OperationTypeCryptoCreateAccount = "CRYPTOCREATEACCOUNT"
	OperationTypeCryptoTransfer      = "CRYPTOTRANSFER"
	OperationTypeTokenAssociate      = "TOKENASSOCIATE"
	OperationTypeTokenBurn           = "TOKENBURN"
	OperationTypeTokenCreate         = "TOKENCREATION"
	OperationTypeTokenDelete         = "TOKENDELETION"
	OperationTypeTokenDissociate     = "TOKENDISSOCIATE" // #nosec
	OperationTypeTokenFreeze         = "TOKENFREEZE"
	OperationTypeTokenGrantKyc       = "TOKENGRANTKYC"
	OperationTypeTokenMint           = "TOKENMINT"
	OperationTypeTokenRevokeKyc      = "TOKENREVOKEKYC"
	OperationTypeTokenUnfreeze       = "TOKENUNFREEZE"
	OperationTypeTokenUpdate         = "TOKENUPDATE"
	OperationTypeTokenWipe           = "TOKENWIPE"

	OperationTypeFee = "FEE"
)

const (
	Blockchain = "Hedera"

	currencySymbol   = "HBAR"
	currencyDecimals = 8
)

var transactionResults map[int32]string
var transactionTypes map[int32]string

var (
	CurrencyHbar = &types.Currency{
		Symbol:   currencySymbol,
		Decimals: currencyDecimals,
		Metadata: map[string]interface{}{
			"issuer": Blockchain,
		},
	}

	SupportedOperationTypes = []string{
		OperationTypeCryptoCreateAccount,
		OperationTypeCryptoTransfer,
		OperationTypeTokenAssociate,
		OperationTypeTokenBurn,
		OperationTypeTokenCreate,
		OperationTypeTokenDelete,
		OperationTypeTokenDissociate,
		OperationTypeTokenFreeze,
		OperationTypeTokenGrantKyc,
		OperationTypeTokenMint,
		OperationTypeTokenRevokeKyc,
		OperationTypeTokenUnfreeze,
		OperationTypeTokenUpdate,
		OperationTypeTokenWipe,
	}
)

func GetTransactionResult(code int32) string {
	transactionResult, ok := transactionResults[code]
	if !ok {
		// If transactionResult does not exist default to general status
		transactionResult = generalErrorStatus
	}
	return transactionResult
}

func GetTransactionResults() map[int32]string {
	return transactionResults
}

func GetTransactionType(code int32) string {
	transactionType, ok := transactionTypes[code]
	if !ok {
		// If transactionType does not exist, map it to UNKNOWN
		transactionType = unknownTransactionType
	}
	return transactionType
}

func GetTransactionTypes() map[int32]string {
	return transactionTypes
}

func init() {
	// Initialize transaction results
	transactionResults = make(map[int32]string)
	transactionResults[-1] = generalErrorStatus
	for code, name := range services.ResponseCodeEnum_name {
		if code > MaxTransactionResult {
			continue
		}

		transactionResults[code] = name
	}

	// Initialize transaction types
	transactionTypes = make(map[int32]string)
	transactionTypes[0] = unknownTransactionType
	body := services.TransactionBody{}
	dataFields := body.ProtoReflect().Descriptor().Oneofs().ByName("data").Fields()
	for i := 0; i < dataFields.Len(); i++ {
		dataField := dataFields.Get(i)
		protoId := int32(dataField.Number())
		if protoId > MaxTransactionType {
			continue
		}

		name := strings.ToUpper(string(dataField.Name()))
		transactionTypes[protoId] = strings.ReplaceAll(name, "_", "")
	}
}
