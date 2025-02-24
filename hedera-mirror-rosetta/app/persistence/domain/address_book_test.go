// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAddressBookTableName(t *testing.T) {
	assert.Equal(t, "address_book", AddressBook{}.TableName())
}
