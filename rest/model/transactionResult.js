// SPDX-License-Identifier: Apache-2.0

import invert from 'lodash/invert';
import {ResponseCodeEnum} from '../gen/services/response_code_pb.js';

const protoToName = {};
for (const name of Object.keys(ResponseCodeEnum)) {
  const id = ResponseCodeEnum[name];
  if (typeof id === 'number') {
    protoToName[id] = name;
  }
}

const nameToProto = invert(protoToName);
const SUCCESS = 'SUCCESS';
const UNKNOWN = 'UNKNOWN';

const getName = (protoId) => {
  return protoToName[protoId] || UNKNOWN;
};

const getProtoId = (name) => {
  return nameToProto[name];
};

const getSuccessProtoIds = () => {
  return [
    Number.parseInt(getProtoId(SUCCESS)),
    Number.parseInt(getProtoId('FEE_SCHEDULE_FILE_PART_UPLOADED')),
    Number.parseInt(getProtoId('SUCCESS_BUT_MISSING_EXPECTED_OPERATION')),
  ];
};

export default {
  getName,
  getProtoId,
  getSuccessProtoIds,
};
