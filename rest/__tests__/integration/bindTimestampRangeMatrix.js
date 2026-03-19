// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {applyResponseJsonMatrix} from '../integrationUtils';

const applyMatrix = (spec) => {
  return [false, true].map((value) => {
    const clone = _.cloneDeep(spec);
    const key = `bindTimestampRange=${value}`;

    clone.name = `${spec.name} with ${key}`;
    clone.setup.config = _.merge(clone.setup.config, {
      query: {
        bindTimestampRange: value,
      },
    });

    return applyResponseJsonMatrix(clone, key);
  });
};

export default applyMatrix;
