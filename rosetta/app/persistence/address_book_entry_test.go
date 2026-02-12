// SPDX-License-Identifier: Apache-2.0

package persistence

import (
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

// run the suite
func TestAddressBookEntryRepositorySuite(t *testing.T) {
	suite.Run(t, new(addressBookEntryRepositorySuite))
}

func TestAddressBookEntryRepositorySuiteNonDefaultShardRealm(t *testing.T) {
	suite.Run(t, &addressBookEntryRepositorySuite{
		shard: 1023,
		realm: 2,
	})
}

type addressBookEntryRepositorySuite struct {
	integrationTest
	suite.Suite

	accountId3                  domain.EntityId
	accountId4                  domain.EntityId
	accountId70                 domain.EntityId
	accountId80                 domain.EntityId
	addressBooks                []*domain.AddressBook
	addressBookEntries          []*domain.AddressBookEntry
	addressBookServiceEndpoints []*domain.AddressBookServiceEndpoint
	realm                       int64
	shard                       int64
	systemEntity                domain.SystemEntity
}

func (suite *addressBookEntryRepositorySuite) SetupSuite() {
	suite.accountId3 = MustEncodeEntityId(suite.shard, suite.realm, 3)
	suite.accountId4 = MustEncodeEntityId(suite.shard, suite.realm, 4)
	suite.accountId70 = MustEncodeEntityId(suite.shard, suite.realm, 70)
	suite.accountId80 = MustEncodeEntityId(suite.shard, suite.realm, 80)
	suite.systemEntity = domain.NewSystemEntity(config.CommonConfig{
		Realm: suite.realm,
		Shard: suite.shard,
	})

	suite.addressBooks = []*domain.AddressBook{
		getAddressBook(9, 0, suite.systemEntity.GetAddressBook102()),
		getAddressBook(10, 19, suite.systemEntity.GetAddressBook101()),
		getAddressBook(20, 0, suite.systemEntity.GetAddressBook101()),
	}
	suite.addressBookEntries = []*domain.AddressBookEntry{
		getAddressBookEntry(9, 0, suite.accountId3),
		getAddressBookEntry(9, 1, suite.accountId4),
		getAddressBookEntry(10, 0, suite.accountId3),
		getAddressBookEntry(10, 1, suite.accountId4),
		getAddressBookEntry(20, 0, suite.accountId80),
		getAddressBookEntry(20, 1, suite.accountId70),
	}
	suite.addressBookServiceEndpoints = []*domain.AddressBookServiceEndpoint{
		{10, "192.168.0.10", 0, 50211},
		{10, "192.168.1.10", 1, 50211},
		{20, "192.168.0.10", 0, 50211},
		{20, "192.168.0.1", 0, 50217},
		{20, "192.168.0.1", 0, 50211},
		{20, "192.168.1.10", 1, 50211},
	}
}

func (suite *addressBookEntryRepositorySuite) TestEntries() {
	// given
	// persist addressbooks before addressbook entries due to foreign key constraint
	db.CreateDbRecords(dbClient, suite.addressBooks, suite.addressBookEntries, suite.addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, suite.accountId80, []string{"192.168.0.1:50211", "192.168.0.1:50217", "192.168.0.10:50211"}},
			{1, suite.accountId70, []string{"192.168.1.10:50211"}},
		},
	}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoEntries() {
	// given
	db.CreateDbRecords(dbClient, suite.addressBooks, suite.addressBookServiceEndpoints)

	expected := &types.AddressBookEntries{Entries: []types.AddressBookEntry{}}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), expected, actual)
	assert.Nil(suite.T(), err)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesNoServiceEndpoints() {
	// given
	db.CreateDbRecords(dbClient, suite.addressBooks, suite.addressBookEntries)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, suite.accountId80, []string{}},
			{1, suite.accountId70, []string{}},
		},
	}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

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
		getAddressBook(10, 19, suite.systemEntity.GetAddressBook102()),
		getAddressBook(20, 0, suite.systemEntity.GetAddressBook101()),
		getAddressBookEntry(10, 0, suite.accountId4),
		getAddressBookEntry(10, 1, suite.accountId3),
		getAddressBookEntry(20, 0, suite.accountId70),
		getAddressBookEntry(20, 1, suite.accountId80),
	)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, suite.accountId70, []string{}},
			{1, suite.accountId80, []string{}},
		},
	}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesDbConnectionError() {
	// given
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), invalidDbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesWithNodeAccountId() {
	// given:
	accountId5 := MustEncodeEntityId(suite.shard, suite.realm, 5)
	accountId6 := MustEncodeEntityId(suite.shard, suite.realm, 6)

	db.CreateDbRecords(dbClient, suite.addressBooks, suite.addressBookEntries, suite.addressBookServiceEndpoints)

	suite.createNode(0, accountId5)
	suite.createNode(1, accountId6)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId5, []string{"192.168.0.1:50211", "192.168.0.1:50217", "192.168.0.10:50211"}},
			{1, accountId6, []string{"192.168.1.10:50211"}},
		},
	}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *addressBookEntryRepositorySuite) TestEntriesWithMixedNodeAccountIds() {
	// given:
	accountId5 := MustEncodeEntityId(suite.shard, suite.realm, 5)

	db.CreateDbRecords(dbClient, suite.addressBooks, suite.addressBookEntries, suite.addressBookServiceEndpoints)

	suite.createNode(0, accountId5)
	suite.createNodeWithNullAccountId(1)

	expected := &types.AddressBookEntries{
		Entries: []types.AddressBookEntry{
			{0, accountId5, []string{"192.168.0.1:50211", "192.168.0.1:50217", "192.168.0.10:50211"}},
			{1, suite.accountId70, []string{"192.168.1.10:50211"}},
		},
	}
	repo := NewAddressBookEntryRepository(suite.systemEntity.GetAddressBook101(), suite.systemEntity.GetAddressBook102(), dbClient)

	// when
	actual, err := repo.Entries(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func MustEncodeEntityId(shard, realm, num int64) domain.EntityId {
	encodedId, err := domain.EntityIdOf(shard, realm, num)
	if err != nil {
		panic(err)
	}

	return encodedId
}

func getAddressBook(start, end int64, fileId domain.EntityId) *domain.AddressBook {
	addressBook := domain.AddressBook{StartConsensusTimestamp: start, FileData: []byte{}, FileId: fileId}
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

func (suite *addressBookEntryRepositorySuite) createNode(nodeId int64, accountId domain.EntityId) {
	dbClient.GetDb().Exec(
		`INSERT INTO node (node_id, account_id, created_timestamp, deleted, timestamp_range)
		 VALUES (?, ?, 1, false, int8range(1, NULL, '[)'))`,
		nodeId,
		accountId.EncodedId,
	)
}

func (suite *addressBookEntryRepositorySuite) createNodeWithNullAccountId(nodeId int64) {
	dbClient.GetDb().Exec(
		`INSERT INTO node (node_id, account_id, created_timestamp, deleted, timestamp_range)
		 VALUES (?, NULL, 1, false, int8range(1, NULL, '[)'))`,
		nodeId,
	)
}
