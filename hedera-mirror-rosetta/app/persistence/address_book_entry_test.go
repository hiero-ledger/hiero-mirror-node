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
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountId3   = domain.MustDecodeEntityId(3)
	accountId4   = domain.MustDecodeEntityId(4)
	accountId70  = domain.MustDecodeEntityId(70)
	accountId80  = domain.MustDecodeEntityId(80)
	addressBooks = []*domain.AddressBook{
		getAddressBook(9, 0, 102),
		getAddressBook(10, 19, 101),
		getAddressBook(20, 0, 101),
	}
	addressBookEntries = []*domain.AddressBookEntry{
		getAddressBookEntry(9, 0, accountId3),
		getAddressBookEntry(9, 1, accountId4),
		getAddressBookEntry(10, 0, accountId3),
		getAddressBookEntry(10, 1, accountId4),
		getAddressBookEntry(20, 0, accountId80),
		getAddressBookEntry(20, 1, accountId70),
	}
	addressBookServiceEndpoints = []*domain.AddressBookServiceEndpoint{
		{10, "192.168.0.10", 0, 50211},
		{10, "192.168.1.10", 1, 50211},
		{20, "192.168.0.10", 0, 50211},
		{20, "192.168.0.1", 0, 50217},
		{20, "192.168.0.1", 0, 50211},
		{20, "192.168.1.10", 1, 50211},
	}
)

// run the suite
func TestAddressBookEntryRepositorySuite(t *testing.T) {
	suite.Run(t, new(addressBookEntryRepositorySuite))
}

type addressBookEntryRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *addressBookEntryRepositorySuite) TestEntries() {
	// given
	// persist addressbooks before addressbook entries due to foreign key constraint
	db.CreateDbRecords(dbClient, addressBooks, addressBookEntries, addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId80, []string{"192.168.0.1:50211", "192.168.0.1:50217", "192.168.0.10:50211"}},
			{1, accountId70, []string{"192.168.1.10:50211"}},
		},
	}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoEntries() {
	// given
	db.CreateDbRecords(dbClient, addressBooks, addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{Entries: []types.AddressBookEntry{}}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoServiceEndpoints() {
	// given
	db.CreateDbRecords(dbClient, addressBooks, addressBookEntries)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId80, []string{}},
			{1, accountId70, []string{}},
		},
	}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoFile101() {
	// given
	db.CreateDbRecords(
		dbClient,
		getAddressBook(10, 19, 102),
		getAddressBook(20, 0, 102),
		getAddressBookEntry(10, 0, accountId4),
		getAddressBookEntry(10, 1, accountId3),
		getAddressBookEntry(20, 0, accountId70),
		getAddressBookEntry(20, 1, accountId80),
	)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId70, []string{}},
			{1, accountId80, []string{}},
		},
	}
	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesDbInvalidNodeAccountId() {
	// given
	db.CreateDbRecords(dbClient, addressBooks, getAddressBookEntry(20, 0, domain.EntityId{EncodedId: -1}))

	repo := NewAddressBookEntryRepository(dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesDbConnectionError() {
	// given
	repo := NewAddressBookEntryRepository(invalidDbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func getAddressBook(start, end int64, fileId int64) *domain.AddressBook {
	addressBook := domain.AddressBook{StartConsensusTimestamp: start, FileId: domain.MustDecodeEntityId(fileId)}
	if end != 0 {
		addressBook.EndConsensusTimestamp = &end
	}
	return &addressBook
}

func getAddressBookEntry(
	consensusTimestamp int64,
	nodeId int64,
	nodeAccountId domain.EntityId,
) *domain.AddressBookEntry {
	return &domain.AddressBookEntry{
		ConsensusTimestamp: consensusTimestamp,
		NodeId:             nodeId,
		NodeAccountId:      nodeAccountId,
	}
}
