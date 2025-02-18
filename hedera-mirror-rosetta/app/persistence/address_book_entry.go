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

package persistence

import (
	"context"
	"database/sql"
	"strings"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	log "github.com/sirupsen/logrus"
)

const (
	fileId101                  int64 = 101
	fileId102                  int64 = 102
	latestNodeServiceEndpoints       = `select
                                    abe.node_id,
                                    abe.node_account_id,
                                    string_agg(ip_address_v4 || ':' || port::text, ','
                                      order by ip_address_v4,port) endpoints
                                  from (
                                    select max(start_consensus_timestamp) from address_book where file_id = @file_id
                                  ) current
                                  join address_book_entry abe on abe.consensus_timestamp = current.max
                                  left join address_book_service_endpoint abse
                                    on abse.consensus_timestamp = current.max and abse.node_id = abe.node_id
                                  group by abe.node_id, abe.node_account_id`
)

type nodeServiceEndpoint struct {
	NodeId        int64
	NodeAccountId domain.EntityId
	Endpoints     string
}

func (n nodeServiceEndpoint) toAddressBookEntry() types.AddressBookEntry {
	endpoints := []string{}
	if n.Endpoints != "" {
		endpoints = strings.Split(n.Endpoints, ",")
	}
	return types.AddressBookEntry{
		NodeId:    n.NodeId,
		AccountId: n.NodeAccountId,
		Endpoints: endpoints,
	}
}

// addressBookEntryRepository struct that has connection to the Database
type addressBookEntryRepository struct {
	dbClient interfaces.DbClient
}

func (aber *addressBookEntryRepository) Entries(ctx context.Context) (*types.AddressBookEntries, *rTypes.Error) {
	db, cancel := aber.dbClient.GetDbWithContext(ctx)
	defer cancel()

	nodes := make([]nodeServiceEndpoint, 0)
	// address book file 101 has service endpoints for nodes, resort to file 102 if 101 doesn't exist
	for _, fileId := range []int64{fileId101, fileId102} {
		if err := db.Raw(
			latestNodeServiceEndpoints,
			sql.Named("file_id", fileId),
		).Scan(&nodes).Error; err != nil {
			log.Error("Failed to get latest node service endpoints", err)
			return nil, errors.ErrDatabaseError
		}

		if len(nodes) != 0 {
			break
		}
	}

	entries := make([]types.AddressBookEntry, 0, len(nodes))
	for _, node := range nodes {
		entries = append(entries, node.toAddressBookEntry())
	}

	return &types.AddressBookEntries{Entries: entries}, nil
}

// NewAddressBookEntryRepository creates an instance of a addressBookEntryRepository struct.
func NewAddressBookEntryRepository(dbClient interfaces.DbClient) interfaces.AddressBookEntryRepository {
	return &addressBookEntryRepository{dbClient}
}
