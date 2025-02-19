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

package types

import (
	"fmt"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

// AddressBookEntry is domain level struct used to represent Rosetta Peer
type AddressBookEntry struct {
	NodeId    int64
	AccountId domain.EntityId
	Endpoints []string
}

// AddressBookEntries is domain level struct used to represent an array of AddressBookEntry
type AddressBookEntries struct {
	Entries []AddressBookEntry
}

// ToRosetta returns an array of Rosetta type Peer
func (abe *AddressBookEntries) ToRosetta() []*types.Peer {
	peers := make([]*types.Peer, len(abe.Entries))
	for i, e := range abe.Entries {
		peers[i] = &types.Peer{
			PeerID: fmt.Sprintf("%d", e.NodeId),
			Metadata: map[string]interface{}{
				"account_id": e.AccountId.String(),
				"endpoints":  e.Endpoints,
			},
		}
	}

	return peers
}
