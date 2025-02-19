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
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
