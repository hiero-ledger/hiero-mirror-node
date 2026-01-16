// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {setupTestParameters} from '../libex/parameters.js';

const urlTag = '/rosetta/construction/combine';

const {options, run} = new TestScenarioBuilder()
  .name('constructionCombine') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = testParameters.baseUrl + urlTag;
    // the public key doesn't have to belong to the account in the payload since it's merely used to verify the signature
    const payload = JSON.stringify({
      network_identifier: testParameters.networkIdentifier,
      unsigned_transaction: testParameters.unsignedTransaction,
      signatures: [
        {
          signing_payload: {
            account_identifier: testParameters.accountIdentifier,
            hex_bytes: testParameters.signingTransaction,
            signature_type: testParameters.signatureType,
          },
          public_key: testParameters.publicKey,
          signature_type: testParameters.signatureType,
          hex_bytes: testParameters.transactionSignature,
        },
      ],
    });
    return http.post(url, payload);
  })
  .check('ConstructionCombine OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
