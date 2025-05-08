// SPDX-License-Identifier: Apache-2.0

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
)

func exampleAddressBookEntries() *AddressBookEntries {
	return &AddressBookEntries{
		[]AddressBookEntry{
			newDummyAddressBookEntry(0, 3, []string{"10.0.0.1:50211"}),
			newDummyAddressBookEntry(1, 4, []string{"192.168.0.5:50211"}),
			newDummyAddressBookEntry(2, 5, []string{"192.168.50.2:50211", "192.168.140.7:50211"}),
			newDummyAddressBookEntry(3, 6, []string{}),
		},
	}
}

func expectedRosettaPeers() []*types.Peer {
	return []*types.Peer{
		newDummyPeer("3", dummyMetadata("0.0.6", []string{})),
		newDummyPeer("0", dummyMetadata("0.0.3", []string{"10.0.0.1:50211"})),
		newDummyPeer("1", dummyMetadata("0.0.4", []string{"192.168.0.5:50211"})),
		newDummyPeer("2", dummyMetadata("0.0.5", []string{"192.168.50.2:50211", "192.168.140.7:50211"})),
	}
}

func TestToRosettaPeers(t *testing.T) {
	// when:
	actual := exampleAddressBookEntries().ToRosetta()

	// then:
	assert.ElementsMatch(t, expectedRosettaPeers(), actual)
}

func newDummyPeer(nodeId string, metadata map[string]interface{}) *types.Peer {
	return &types.Peer{
		PeerID:   nodeId,
		Metadata: metadata,
	}
}

func newDummyAddressBookEntry(nodeId int64, accountId int64, endpoints []string) AddressBookEntry {
	return AddressBookEntry{
		NodeId:    nodeId,
		AccountId: domain.MustDecodeEntityId(accountId),
		Endpoints: endpoints,
	}
}

func dummyMetadata(accountId string, endpoints []string) map[string]interface{} {
	return map[string]interface{}{
		"account_id": accountId,
		"endpoints":  endpoints,
	}
}
