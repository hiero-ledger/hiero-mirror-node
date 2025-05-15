// SPDX-License-Identifier: Apache-2.0

package db

import (
	"context"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"gorm.io/gorm"
)

type client struct {
	db               *gorm.DB
	statementTimeout int
}

func (d *client) GetDb() *gorm.DB {
	return d.db
}

func (d *client) GetDbWithContext(ctx context.Context) (*gorm.DB, context.CancelFunc) {
	if d.statementTimeout <= 0 {
		db := d.db
		if ctx != nil {
			db = db.WithContext(ctx)
		}
		return db, noop
	}

	if ctx == nil {
		ctx = context.Background()
	}

	childCtx, cancel := context.WithTimeout(ctx, time.Duration(d.statementTimeout)*time.Second)
	return d.db.WithContext(childCtx), cancel
}

func NewDbClient(db *gorm.DB, statementTimeout int) interfaces.DbClient {
	return &client{db: db, statementTimeout: statementTimeout}
}

func noop() {
	// empty cancel function
}
