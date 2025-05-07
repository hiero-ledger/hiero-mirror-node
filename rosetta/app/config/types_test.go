// SPDX-License-Identifier: Apache-2.0

package config

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDbGetDsn(t *testing.T) {
	db := Db{
		Host:     "127.0.0.1",
		Name:     "mirror_node",
		Password: "mirror_user_pass",
		Pool: Pool{
			MaxIdleConnections: 20,
			MaxLifetime:        2000,
			MaxOpenConnections: 30,
		},
		Port:     5432,
		Username: "mirror_user",
	}
	expected := "host=127.0.0.1 port=5432 user=mirror_user dbname=mirror_node password=mirror_user_pass sslmode=disable"

	assert.Equal(t, expected, db.GetDsn())
}
