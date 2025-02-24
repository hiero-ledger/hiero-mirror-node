// SPDX-License-Identifier: Apache-2.0

import ed25519 from '../ed25519';

/**
 * Unit test for derToEd25519.js  to perform on the resultant SQL query.
 */

describe('Ed25519 tests', () => {
  test(`Valid conversion:`, () => {
    const validDer = '302a300506032b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc';
    const validDecoded = '7a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc';

    let response = ed25519.derToEd25519(validDer);
    expect(response).toEqual(validDecoded);

    // Uppercase key
    response = ed25519.derToEd25519(validDer.toUpperCase());
    expect(response).toEqual(validDecoded);
  });

  test(`Invalid conversion:`, () => {
    const invalidDers = [
      // Corrupt beginning - 30aa
      '30aa300506032b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc',
      // Wrong length
      '302a300506032b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5',
      // Wrong bytes in between - 02a300506aa
      '302a300506aa2b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc',
      // Valid decoded key
      '7a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc',
      // Other invalid values
      '',
      0,
      {},
      {key: 1234},
    ];

    for (const invalidDer of invalidDers) {
      const response = ed25519.derToEd25519(invalidDer);
      expect(response).toBe(null);
    }
  });
});
