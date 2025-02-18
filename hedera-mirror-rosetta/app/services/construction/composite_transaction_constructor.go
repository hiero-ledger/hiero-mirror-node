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
	"context"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	log "github.com/sirupsen/logrus"
)

type transactionConstructorWithType interface {
	BaseTransactionConstructor
	GetDefaultMaxTransactionFee() types.HbarAmount
	GetOperationType() string
	GetSdkTransactionType() string
}

type compositeTransactionConstructor struct {
	constructorsByOperationType   map[string]transactionConstructorWithType
	constructorsByTransactionType map[string]transactionConstructorWithType
}

func (c *compositeTransactionConstructor) Construct(
	ctx context.Context,
	operations types.OperationSlice,
) (hiero.TransactionInterface, []types.AccountId, *rTypes.Error) {
	h, err := c.validate(operations)
	if err != nil {
		return nil, nil, err
	}

	return h.Construct(ctx, operations)
}

func (c *compositeTransactionConstructor) GetDefaultMaxTransactionFee(operationType string) (
	types.HbarAmount,
	*rTypes.Error,
) {
	h, ok := c.constructorsByOperationType[operationType]
	if !ok {
		return types.HbarAmount{}, errors.ErrInvalidOperationType
	}
	return h.GetDefaultMaxTransactionFee(), nil
}

func (c *compositeTransactionConstructor) Parse(ctx context.Context, transaction hiero.TransactionInterface) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	name := reflect.TypeOf(transaction).Elem().Name()
	h, ok := c.constructorsByTransactionType[name]
	if !ok {
		log.Errorf("No constructor to parse constructed transaction %s", name)
		return nil, nil, errors.ErrInternalServerError
	}

	return h.Parse(ctx, transaction)
}

func (c *compositeTransactionConstructor) Preprocess(
	ctx context.Context,
	operations types.OperationSlice,
) ([]types.AccountId, *rTypes.Error) {
	h, err := c.validate(operations)
	if err != nil {
		return nil, err
	}

	return h.Preprocess(ctx, operations)
}

func (c *compositeTransactionConstructor) addConstructor(constructor transactionConstructorWithType) {
	c.constructorsByOperationType[constructor.GetOperationType()] = constructor
	c.constructorsByTransactionType[constructor.GetSdkTransactionType()] = constructor
}

func (c *compositeTransactionConstructor) validate(operations types.OperationSlice) (
	transactionConstructorWithType,
	*rTypes.Error,
) {
	if len(operations) == 0 {
		return nil, errors.ErrEmptyOperations
	}

	operationType := operations[0].Type
	for _, operation := range operations[1:] {
		if operation.Type != operationType {
			return nil, errors.ErrMultipleOperationTypesPresent
		}
	}

	h, ok := c.constructorsByOperationType[operationType]
	if !ok {
		log.Errorf("Operation type %s is not supported", operationType)
		return nil, errors.ErrOperationTypeUnsupported
	}

	return h, nil
}

func NewTransactionConstructor() TransactionConstructor {
	c := &compositeTransactionConstructor{
		constructorsByOperationType:   make(map[string]transactionConstructorWithType),
		constructorsByTransactionType: make(map[string]transactionConstructorWithType),
	}

	c.addConstructor(newCryptoCreateTransactionConstructor())
	c.addConstructor(newCryptoTransferTransactionConstructor())

	return c
}
