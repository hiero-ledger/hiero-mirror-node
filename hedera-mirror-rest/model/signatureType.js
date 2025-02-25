// SPDX-License-Identifier: Apache-2.0

const protoToName = {
  2: 'CONTRACT',
  3: 'ED25519',
  4: 'RSA_3072',
  5: 'ECDSA_384',
  6: 'ECDSA_SECP256K1',
};

const UNKNOWN = 'UNKNOWN';

const getName = (protoId) => {
  return protoToName[protoId] || UNKNOWN;
};

export default {
  getName,
};
