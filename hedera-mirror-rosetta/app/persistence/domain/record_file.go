// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameRecordFile = "record_file"

type RecordFile struct {
	ConsensusStart   int64
	ConsensusEnd     int64 `gorm:"primaryKey"`
	Count            int64
	DigestAlgorithm  int
	FileHash         string
	HapiVersionMajor int
	HapiVersionMinor int
	HapiVersionPatch int
	Hash             string
	Index            int64
	LoadEnd          int64
	LoadStart        int64
	Name             string
	NodeId           int64
	PrevHash         string
	Version          int
}

// TableName returns record file table name
func (RecordFile) TableName() string {
	return tableNameRecordFile
}
