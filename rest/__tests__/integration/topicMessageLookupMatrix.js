// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

const applyMatrix = (spec) => {
  return [false, true].map((value) => {
    const clone = _.cloneDeep(spec);
    clone.name = `${spec.name} with topicMessageLookup=${value}`;
    clone.setup.config = _.merge(clone.setup.config, {
      query: {
        topicMessageLookup: value,
      },
    });
    return clone;
  });
};

export default applyMatrix;
