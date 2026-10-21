// SPDX-License-Identifier: Apache-2.0

const concat = (...parts) => {
  let length = 0;
  for (const part of parts) {
    length += part.length;
  }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) {
    bytes.set(part, offset);
    offset += part.length;
  }
  return bytes;
};

const varint = (value) => {
  const bytes = [];
  let remaining = value;
  while (remaining > 127) {
    bytes.push((remaining % 128) + 128);
    remaining = Math.floor(remaining / 128);
  }
  bytes.push(remaining);
  return new Uint8Array(bytes);
};

const fieldVarint = (fieldNumber, value) => concat(varint((fieldNumber << 3) | 0), varint(value));

const fieldBytes = (fieldNumber, payload) => concat(varint((fieldNumber << 3) | 2), varint(payload.length), payload);

const accountNum = (accountId) => {
  const num = Number(String(accountId).split('.').pop());
  if (!Number.isInteger(num) || num < 0) {
    throw new Error(`Invalid account id ${accountId}`);
  }
  return num;
};

// AccountAmount.amount is a protobuf sint64. Varints are unsigned, so a signed amount is
// mapped to an unsigned one first: 0, -1, 1, -2, 2 become 0, 1, 2, 3, 4.
const encodeSignedAmount = (amount) => {
  if (amount < 0) {
    return -amount * 2 - 1;
  }
  return amount * 2;
};

const accountAmount = (accountId, amount) =>
  concat(fieldBytes(1, fieldVarint(3, accountNum(accountId))), fieldVarint(2, encodeSignedAmount(amount)));

// Signed CryptoTransfer of 1 tinybar from senderAccountId to receiverAccountId.
// Account ids are the num or shard.realm.num values already set for rest-java k6.
const cryptoTransferTransaction = (senderAccountId, receiverAccountId) => {
  const transfers = concat(
    fieldBytes(1, accountAmount(senderAccountId, -1)),
    fieldBytes(1, accountAmount(receiverAccountId, 1))
  );
  const body = fieldBytes(14, fieldBytes(1, transfers));
  const signedTransaction = fieldBytes(1, body);
  return fieldBytes(5, signedTransaction).buffer;
};

export {cryptoTransferTransaction};
