// SPDX-License-Identifier: Apache-2.0

import {isV2Schema} from '../testutils';

const nullifyPayerAccountId = async () => ownerPool.queryQuietly('update transaction_hash set payer_account_id = null');

const applyMatrix = (spec) => {
  if (isV2Schema()) {
    return [spec];
  }

  const defaultSpec = {...spec};

  defaultSpec.name = `${defaultSpec.name} - default`;

  const nullPayerAccountIdSpec = {...spec};
  nullPayerAccountIdSpec.name = `${nullPayerAccountIdSpec.name} - null transaction_hash.payer_account_id`;
  nullPayerAccountIdSpec.postSetup = nullifyPayerAccountId;

  return [defaultSpec, nullPayerAccountIdSpec];
};

export default applyMatrix;
