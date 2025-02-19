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

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
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
