// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAccountBalanceTableName(t *testing.T) {
	assert.Equal(t, "account_balance", AccountBalance{}.TableName())
}
