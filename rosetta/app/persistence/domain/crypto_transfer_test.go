// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCryptoTransferTableName(t *testing.T) {
	assert.Equal(t, "crypto_transfer", CryptoTransfer{}.TableName())
}
