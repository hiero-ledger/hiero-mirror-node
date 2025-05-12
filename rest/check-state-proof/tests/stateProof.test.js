// SPDX-License-Identifier: Apache-2.0

import StateProofHandler from '../stateProofHandler';
import {readJSONFile} from '../utils';

describe('stateproof sample test', () => {
  test('transaction 0.0.94139-1570800748-313194300 in v2 sample json', () => {
    const stateProofJson = readJSONFile('sample/v2/stateProofSample.json');
    const stateProofManager = new StateProofHandler(stateProofJson, '0.0.94139-1570800748-313194300');
    expect(stateProofManager.runStateProof()).toBe(true);
  });

  test('transaction 0.0.88-1614972043-671238000 in v5 sample json', () => {
    const stateProofJson = readJSONFile('sample/v5/stateProofSample.json');
    const stateProofManager = new StateProofHandler(stateProofJson, '0.0.88-1614972043-671238000');
    expect(stateProofManager.runStateProof()).toBe(true);
  });
});
