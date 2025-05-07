// SPDX-License-Identifier: Apache-2.0

package errors

import (
	"github.com/coinbase/rosetta-sdk-go/types"
)

const (
	AccountNotFound                   = "Account not found"
	BlockNotFound                     = "Block not found"
	CreateAccountDbIdFailed           = "An error occurred while creating Account ID from encoded DB ID: %x"
	DatabaseError                     = "Database error"
	EmptyOperations                   = "Empty operations provided"
	EndpointNotSupportedInOfflineMode = "Endpoint not supported in offline mode"
	InternalServerError               = "Internal Server Error"
	InvalidAccount                    = "Invalid Account provided"
	InvalidAmount                     = "Invalid Amount provided"
	InvalidArgument                   = "Invalid argument"
	InvalidCurrency                   = "Invalid currency"
	InvalidCurveType                  = "Invalid curve type"
	InvalidOperationMetadata          = "Invalid operation metadata"
	InvalidOperationType              = "Invalid operation type"
	InvalidOperations                 = "Invalid operations"
	InvalidOperationsAmount           = "Invalid Operations amount provided"
	InvalidOperationsTotalAmount      = "Operations total amount must be 0"
	InvalidOptions                    = "Invalid options"
	InvalidPublicKey                  = "Invalid Public Key provided"
	InvalidSignatureType              = "Unsupported signature type"
	InvalidSignatureVerification      = "Invalid signature verification"
	InvalidToken                      = "Invalid token"
	InvalidTransaction                = "Invalid transaction"
	InvalidTransactionIdentifier      = "Invalid Transaction Identifier provided"
	InvalidTransactionMemo            = "Invalid transaction memo"
	MissingNodeAccountIdMetadata      = "Missing node_account_id metadata"
	MultipleOperationTypesPresent     = "Only one Operation Type must be present"
	NoSignature                       = "No signature"
	NodeAccountIdsEmpty               = "Node account IDs are empty"
	NodeIsStarting                    = "Node is starting"
	NotImplemented                    = "Not implemented"
	OperationResultsNotFound          = "Operation Results not found"
	OperationTypeUnsupported          = "Operation type unsupported"
	OperationTypesNotFound            = "Operation Types not found"
	StartMustNotBeAfterEnd            = "Start must not be after end"
	TokenNotFound                     = "Token not found"
	TransactionDecodeFailed           = "Transaction Decode failed"
	TransactionFreezeFailed           = "Transaction freeze failed"
	TransactionHashFailed             = "Transaction hash failed"
	TransactionInvalidType            = "Transaction invalid type"
	TransactionMarshallingFailed      = "Transaction marshalling failed"
	TransactionNotFound               = "Transaction not found"
	TransactionSubmissionFailed       = "Transaction submission failed"
	TransactionUnmarshallingFailed    = "Transaction unmarshalling failed"
)

var (
	ErrAccountNotFound                   = newError(AccountNotFound, 100, true)
	ErrBlockNotFound                     = newError(BlockNotFound, 101, true)
	ErrInvalidAccount                    = newError(InvalidAccount, 102, false)
	ErrInvalidAmount                     = newError(InvalidAmount, 103, false)
	ErrInvalidOperationsAmount           = newError(InvalidOperationsAmount, 104, false)
	ErrInvalidOperationsTotalAmount      = newError(InvalidOperationsTotalAmount, 105, false)
	ErrInvalidPublicKey                  = newError(InvalidPublicKey, 106, false)
	ErrInvalidSignatureVerification      = newError(InvalidSignatureVerification, 107, false)
	ErrInvalidTransactionIdentifier      = newError(InvalidTransactionIdentifier, 108, false)
	ErrMultipleOperationTypesPresent     = newError(MultipleOperationTypesPresent, 109, false)
	ErrNodeIsStarting                    = newError(NodeIsStarting, 110, true)
	ErrNotImplemented                    = newError(NotImplemented, 111, false)
	ErrOperationResultsNotFound          = newError(OperationResultsNotFound, 112, true)
	ErrOperationTypesNotFound            = newError(OperationTypesNotFound, 113, true)
	ErrStartMustNotBeAfterEnd            = newError(StartMustNotBeAfterEnd, 114, false)
	ErrTransactionDecodeFailed           = newError(TransactionDecodeFailed, 115, false)
	ErrTransactionMarshallingFailed      = newError(TransactionMarshallingFailed, 116, false)
	ErrTransactionUnmarshallingFailed    = newError(TransactionUnmarshallingFailed, 117, false)
	ErrTransactionSubmissionFailed       = newError(TransactionSubmissionFailed, 118, false)
	ErrTransactionNotFound               = newError(TransactionNotFound, 119, true)
	ErrEmptyOperations                   = newError(EmptyOperations, 120, false)
	ErrTransactionInvalidType            = newError(TransactionInvalidType, 121, false)
	ErrTransactionHashFailed             = newError(TransactionHashFailed, 122, false)
	ErrTransactionFreezeFailed           = newError(TransactionFreezeFailed, 123, false)
	ErrInvalidArgument                   = newError(InvalidArgument, 124, false)
	ErrDatabaseError                     = newError(DatabaseError, 125, true)
	ErrInvalidOperationMetadata          = newError(InvalidOperationMetadata, 126, false)
	ErrOperationTypeUnsupported          = newError(OperationTypeUnsupported, 127, false)
	ErrInvalidOperationType              = newError(InvalidOperationType, 128, false)
	ErrNoSignature                       = newError(NoSignature, 129, false)
	ErrInvalidOperations                 = newError(InvalidOperations, 130, false)
	ErrInvalidToken                      = newError(InvalidToken, 131, false)
	ErrTokenNotFound                     = newError(TokenNotFound, 132, false)
	ErrInvalidTransaction                = newError(InvalidTransaction, 133, false)
	ErrInvalidCurrency                   = newError(InvalidCurrency, 134, false)
	ErrInvalidSignatureType              = newError(InvalidSignatureType, 135, false)
	ErrEndpointNotSupportedInOfflineMode = newError(EndpointNotSupportedInOfflineMode, 136, false)
	ErrInvalidCurveType                  = newError(InvalidCurveType, 137, false)
	ErrInvalidOptions                    = newError(InvalidOptions, 138, false)
	ErrInvalidTransactionMemo            = newError(InvalidTransactionMemo, 139, false)
	ErrNodeAccountIdsEmpty               = newError(NodeAccountIdsEmpty, 140, true)
	ErrMissingNodeAccountIdMetadata      = newError(MissingNodeAccountIdMetadata, 141, false)
	ErrInternalServerError               = newError(InternalServerError, 500, true)

	Errors = make([]*types.Error, 0)
)

func AddErrorDetails(err *types.Error, key, description string) *types.Error {
	clone := *err
	clone.Details = make(map[string]interface{})
	for k, v := range err.Details {
		clone.Details[k] = v
	}
	clone.Details[key] = description
	return &clone
}

func newError(message string, statusCode int32, retriable bool) *types.Error {
	err := &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
	Errors = append(Errors, err)

	return err
}
