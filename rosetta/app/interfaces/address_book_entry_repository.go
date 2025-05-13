// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
)

// AddressBookEntryRepository Interface that all AddressBookEntryRepository structs must implement
type AddressBookEntryRepository interface {

	// Entries return all current address book Entries
	Entries(ctx context.Context) (*types.AddressBookEntries, *rTypes.Error)
}
