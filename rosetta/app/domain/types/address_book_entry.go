// SPDX-License-Identifier: Apache-2.0

package types

import (
	"fmt"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
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
