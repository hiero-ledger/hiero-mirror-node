// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestRecordFileTableName(t *testing.T) {
	assert.Equal(t, "record_file", RecordFile{}.TableName())
}
