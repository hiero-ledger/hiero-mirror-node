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

package db

import (
	"context"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
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
