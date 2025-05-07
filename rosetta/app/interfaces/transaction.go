// SPDX-License-Identifier: Apache-2.0

package interfaces

import "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

// Transaction defines the transaction methods used by constructor service
// Remove the interface when SDK adds support of hiero.TransactionIsFrozen and
// hiero.TransactionGetDefaultMaxTransactionFee
type Transaction interface {

	// IsFrozen returns if the transaction is frozen
	IsFrozen() bool

	// GetDefaultMaxTransactionFee returns the default max transaction fee set for the Transaction
	GetDefaultMaxTransactionFee() hiero.Hbar
}
