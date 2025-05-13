// SPDX-License-Identifier: Apache-2.0

package db

import (
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

// run the suite
func TestDbSuite(t *testing.T) {
	suite.Run(t, new(dbSuite))
}

type dbSuite struct {
	suite.Suite
	dbResource db.DbResource
}

func (suite *dbSuite) SetupSuite() {
	suite.dbResource = db.SetupDb(false)
}

func (suite *dbSuite) TearDownSuite() {
	db.TearDownDb(suite.dbResource)
}

func (suite *dbSuite) TestConnectToDb() {
	dbClient := ConnectToDb(suite.dbResource.GetDbConfig())
	err := dbClient.GetDb().Exec("select 1").Error
	assert.Nil(suite.T(), err)
}

func (suite *dbSuite) TestConnectToDbInvalidPassword() {
	dbConfig := suite.dbResource.GetDbConfig()
	dbConfig.Password = "bad_password_dab"
	dbClient := ConnectToDb(dbConfig)
	err := dbClient.GetDb().Exec("select 1").Error
	assert.NotNil(suite.T(), err)
}
