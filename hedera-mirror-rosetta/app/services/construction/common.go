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

package construction

import (
	"encoding/json"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type commonTransactionConstructor struct {
	defaultMaxFee   types.HbarAmount
	operationType   string
	transactionType string
	validate        *validator.Validate
}

func (c *commonTransactionConstructor) GetDefaultMaxTransactionFee() types.HbarAmount {
	return c.defaultMaxFee
}

func (c *commonTransactionConstructor) GetOperationType() string {
	return c.operationType
}

func (c *commonTransactionConstructor) GetSdkTransactionType() string {
	return c.transactionType
}

func newCommonTransactionConstructor(
	transaction interfaces.Transaction,
	operationType string,
) commonTransactionConstructor {
	defaultMaxFee := types.HbarAmount{Value: transaction.GetDefaultMaxTransactionFee().AsTinybar()}
	transactionType := reflect.TypeOf(transaction).Elem().Name()
	return commonTransactionConstructor{
		defaultMaxFee:   defaultMaxFee,
		operationType:   operationType,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

type payerMetadata struct {
	Payer *hiero.AccountID `json:"payer" validate:"required"`
}

func compareCurrency(currencyA *rTypes.Currency, currencyB *rTypes.Currency) bool {
	if currencyA == currencyB {
		return true
	}

	if currencyA == nil || currencyB == nil {
		return false
	}

	if currencyA.Symbol != currencyB.Symbol ||
		currencyA.Decimals != currencyB.Decimals ||
		!reflect.DeepEqual(currencyA.Metadata, currencyB.Metadata) {
		return false
	}

	return true
}

func isNonEmptyPublicKey(key hiero.Key) bool {
	pk, ok := key.(hiero.PublicKey)
	if !ok {
		return false
	}

	return len(pk.Bytes()) != 0
}

func isZeroAccountId(accountId hiero.AccountID) bool {
	return accountId.Shard == 0 && accountId.Realm == 0 && accountId.Account == 0
}

func parseOperationMetadata(
	validate *validator.Validate,
	out interface{},
	metadatas ...map[string]interface{},
) *rTypes.Error {
	metadata := make(map[string]interface{})

	for _, m := range metadatas {
		for k, v := range m {
			metadata[k] = v
		}
	}

	data, err := json.Marshal(metadata)
	if err != nil {
		return errors.ErrInvalidOperationMetadata
	}

	if err := json.Unmarshal(data, out); err != nil {
		log.Errorf("Failed to unmarshal operation metadata: %s", err)
		return errors.ErrInvalidOperationMetadata
	}

	if validate != nil {
		if err := validate.Struct(out); err != nil {
			log.Errorf("Failed to validate metadata: %s", err)
			return errors.ErrInvalidOperationMetadata
		}
	}

	return nil
}

func validateOperations(operations types.OperationSlice, size int, opType string, expectNilAmount bool) *rTypes.Error {
	if len(operations) == 0 {
		return errors.ErrEmptyOperations
	}

	if size != 0 && len(operations) != size {
		return errors.ErrInvalidOperations
	}

	for _, operation := range operations {
		if expectNilAmount && operation.Amount != nil {
			return errors.ErrInvalidOperations
		}

		if !expectNilAmount && operation.Amount == nil {
			return errors.ErrInvalidOperations
		}

		if operation.Type != opType {
			return errors.ErrInvalidOperationType
		}
	}

	return nil
}
