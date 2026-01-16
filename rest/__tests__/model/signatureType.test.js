// SPDX-License-Identifier: Apache-2.0

import {SignatureType} from '../../model';

describe('getName', () => {
  test('Valid', () => {
    expect(SignatureType.getName(2)).toEqual('CONTRACT');
    expect(SignatureType.getName(3)).toEqual('ED25519');
    expect(SignatureType.getName(4)).toEqual('RSA_3072');
    expect(SignatureType.getName(5)).toEqual('ECDSA_384');
    expect(SignatureType.getName(6)).toEqual('ECDSA_SECP256K1');
  });

  test('Unknown', () => {
    expect(SignatureType.getName(null)).toEqual('UNKNOWN');
    expect(SignatureType.getName(undefined)).toEqual('UNKNOWN');
    expect(SignatureType.getName(-2)).toEqual('UNKNOWN');
    expect(SignatureType.getName(1)).toEqual('UNKNOWN');
    expect(SignatureType.getName(9999999)).toEqual('UNKNOWN');
    expect(SignatureType.getName('sig')).toEqual('UNKNOWN');
  });
});
