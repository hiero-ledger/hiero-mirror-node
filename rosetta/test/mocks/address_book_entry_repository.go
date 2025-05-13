// SPDX-License-Identifier: Apache-2.0

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

var NilEntries *types.AddressBookEntries

type MockAddressBookEntryRepository struct {
	mock.Mock
}

func (m *MockAddressBookEntryRepository) Entries(ctx context.Context) (*types.AddressBookEntries, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.AddressBookEntries), args.Get(1).(*rTypes.Error)
}
