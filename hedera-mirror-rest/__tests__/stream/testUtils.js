// SPDX-License-Identifier: Apache-2.0

import appRoot from 'app-root-path';
import fs from 'fs';
import path from 'path';

const recordStreamsPath = path.join(appRoot.toString(), '__tests__', 'data', 'recordstreams');
const v2RecordStreamsPath = path.join(recordStreamsPath, 'v2');
const v5RecordStreamsPath = path.join(recordStreamsPath, 'v5');
const v6RecordStreamsPath = path.join(recordStreamsPath, 'v6');

const v5CompactObject = {
  head: Buffer.from('AAAABQAAAAAAAAALAAAAAAAAAAE=', 'base64'),
  startRunningHashObject: Buffer.from(
    '9CLag6JRdB4AAAABWP+BGwAAADCud7M8enNR1VnLI6uc1Ln222yhqC8R8FfNFiq21' + 'nQfl/q/scOcEsOYkwfEI8BRfJY=',
    'base64'
  ),
  hashesBefore: [Buffer.from('INTQVAfkXFE7r5lamB6cMSYHk7M/vo4tOWJuEMhGQtyAx4Wpo4IJZN8WrbtuwQ5k', 'base64')],
  recordStreamObject: Buffer.from(
    '43CSm6VCnYsAAAABAAAApAooCBYqJAoQCLDqARDf0hsaBgiwxZSCBhIQCLDqARDsvhsaB' +
      'gjA4ZSCBhIw7vNajFsqgp9b8TTqjKAxfxitKWgolfilwaWsfWQxW6dO+vnYekUA2atxFHCs7incGgsIoLOUggYQmK6iQiITCgsIk7' +
      'OUggYQpsymfxIEGPOlFjC5igRSIAoICgIYBhDCpCkKCAoCGGIQltUHCgoKBBjzpRYQ1/kwAAAAqSqmAQo8ChMKCwiTs5SCBhCmzKZ' +
      '/EgQY86UWEgIYBhiAwtcvIgIIeHIYChYKCgoEGPOlFhDl5CgKCAoCGAYQ5uQoEmYKZAog0eqX8yNwOga24Q+NhFG70E6wdKKlmnO6' +
      'HsIoJ7ummVEaQIdN4PDVgJ4YvwocjDUPr/x5YjVXLD0xozvGX6PksQMTo8FvDGfkdOFAevnUxj5ATnSGEMRh/9JZ5kdQeJVVKQI=',
    'base64'
  ),
  hashesAfter: [
    Buffer.from('xmnu4v4hvSyASngNeo1UNQlihHkLmXTpML2+KQZQ1815J0KGa9eVRBNK29YmDUHZ', 'base64'),
    Buffer.from('kYqZBpNgMXbbCiDoGlq4TrOY6unqQPaXeNCeFAmhsxMsMewIdkTTJvHezXAuOAKS', 'base64'),
    Buffer.from('V1jWi0tiFxgBZzWeir2NHmS4b/HCvd9Zr+DEMaHti0uUJrNAq6Nz4xZ8VO2EyygI', 'base64'),
    Buffer.from('yxNUNITQnYcKV78iPnsS62aYYwxjwIL2yzGzo3zcRw2R3+DwKMeXXFeRmDHsA7m5', 'base64'),
    Buffer.from('Se83t98i5KUcD8jItkXxehdQte2u4QepQ0GXuQufHiKPf+NdsoWFTkYHpFs3JRyF', 'base64'),
  ],
  endRunningHashObject: Buffer.from(
    '9CLag6JRdB4AAAABWP+BGwAAADCnPUjHQKqq+l6HnYdlD17Zt15RTB5ZCLaGD8+37c3' + 'gCTVQ+ENgVbpWAY9G1yAAGjE=',
    'base64'
  ),
  blockNumber: null,
};

const v6CompactObject = {
  head: Buffer.from('AAAABgAAAAAAAAAcAAAAAA==', 'base64'),
  startRunningHashObject: Buffer.from(
    'f422da83a251741e0000000158ff811b00000030' +
      '5beae2b0a79d74637dfb1a054cb5d6a741fd5a2c503ddeee4b55be0e949dab9e1f82fc5f312637185d10a0d119db4146',
    'hex'
  ),
  hashesBefore: [
    '482e023ef8eb7071d66c9e7a85cdb095b74e4215a756d8783d3c49c8301d097a8e65c0fc590b0cd4841aee1d4113f7db',
    '7a3171b44cd92aad89187e8cda75dc5c66cdb78b02b1a7641adf34e9a01402ed7275cd0686a4c73fcdd8286139db1de3',
    '0fd17c5477d0c723e240bb1a20141d7062fecd6e3068c910685fc75e39089174385b7eb42bb329054c611051e1422bf2',
    '409b5c3a3cb2aa3ade5e7d54c897909a80a3d6f6f7a9364391299685112b0c31217d343f6da688d74654dec8ad947e54',
    '753636b32e74740001b5ea95ac3068a4cf41c5d905236b5b591238d2cd3e290762c7839e7bcb9711028fe1201a64c332',
  ].map((h) => Buffer.from(h, 'hex')),
  recordStreamObject: Buffer.from(
    '43CSm6VCnYsAAAABAAAAvwogCBYqHAoMCAEQDBoGCICumaQPEgwIARAPGgYIgK6ZpA8SMFSht00u3ifHfOqDFJxFn2829Lfb' +
      '8tDebW+8FfFQFT4H/FCG8zPnv+PkzGjxPiiytxoMCJrQ+JYGEKOup8UBIhIKDAiR0PiWBhDh+a6cARICGFoqHERldk9wcyBTeW50' +
      'aGV0aWMgMi4wIFRlc3Rpbmcw5pQFUiUKBgoCGAIQAgoHCgIYBxDMNQoICgIYWhDNqQoKCAoCGGIQgPQJAAAAwCq9AQpTChIKDAiR' +
      '0PiWBhDh+a6cARICGFoSAhgHGIDC1y8iAgh4MhxEZXZPcHMgU3ludGhldGljIDIuMCBUZXN0aW5nchIKEAoGCgIYAhACCgYKAhha' +
      'EAESZgpkCiDCSaMjyHj1teLazNptcx5v3DL4cCKNHNT65VnZR9vDbBpA6cQPMB3yXP6hgSb3+5SYsu0+alJzdATvTCYiJm8Xb0y2' +
      'feYxVNV2QX2fd4Cu4TY/rYgaNBYBYTdOlKQN/VsMAw==',
    'base64'
  ),
  hashesAfter: [
    'd82a33b9b01d4ea2e8da1d01fb129c402429338d3655c1c30b878338073afffdbfcc0a6978b32be82f5323c7ddb711dc',
    '69a3df3aecd7dbd9ab3795f162245b3f64b1209cf1daa5e57fe809ef9150d83455bc7b7d5f9288a9188a6051a46edc3c',
    'ff725b38357e5a5be05a9784c045b8bc42f544aa4d362d82f436102db858d7e639f6dcb4fc302fdb99a890edf056a02b',
    '0d1497587d70f3800a19c4ee44852b22dfd8cca48d6f7be44615e5157738e450cc3fb55c6195fe81773625affa0f42c1',
    '5ae4268d7b4cf01aa3a09f34afa858c204a643fc243bc5b8bfe919eb91ea5d27030301de56a484b0f8d6db795ea640c1',
    'f22cd29e3a8e01a3382b8fe294e81478715aafe13aa2c7890a17b112aa12515a9a9e7f15c63a15441cbd7f5249c8032f',
    '5fb44a7ab6c89088f2456391db4656427a0edd68c35da18c5c2c02e6e5165eb96b1d74fd78abc370a297552080d76a63',
    '0c91b2f4f61ad71cc0cfb078d7d2a1c379ff58847399f909c845af6f69f052e21b25e9a33c3400a55fe8f06ea20fbff4',
    'd82dbfcbb6692f886d1d3a0c1f988a08d438d82d12348c3de55b5797ba3c61f02a6aa8e0d6cf14581002d156141d281b',
    'd8d1863a26020e8246d04f5931a278a947116a389a09095ee479b21f5fe78adcbbb7f216d2e998ceecab2f2fe2d19b4e',
    'a434963320c3e0eaaf68a2f398820be9b4726c0b6376633e5dc6566f74bea361bec969259a01b3d399e75fd03d3924d5',
  ].map((h) => Buffer.from(h, 'hex')),
  endRunningHashObject: Buffer.from(
    'f422da83a251741e0000000158ff811b00000030' +
      '091160ab978e8c6056ee06249b12e98044a0ac8a008bc65d8bd130d1c011fc68081825de90f6a77fbb7d552dcc3bc804',
    'hex'
  ),
  blockNumber: Buffer.from('AAAAAAAEKgg=', 'base64'),
};

const commonV5Checks = [
  {
    func: 'containsTransaction',
    args: ['0.0.365299-1615141267-266970662'],
    expected: true,
  },
  {
    func: 'containsTransaction',
    args: ['0.0.1-123-123456789'],
    expected: false,
  },
  {
    func: 'getFileHash',
    args: [],
    expected: null,
  },
  {
    func: 'getVersion',
    args: [],
    expected: 5,
  },
  {
    func: 'toCompactObject',
    args: ['0.0.365299-1615141267-266970662'],
    expected: v5CompactObject,
  },
  {
    func: 'toCompactObject',
    args: ['0.0.1-123-123456789'],
    expectErr: true,
  },
];

const commonV6Checks = [
  {
    func: 'containsTransaction',
    args: ['0.0.90-1658726417-327924961'],
    expected: true,
  },
  {
    func: 'containsTransaction',
    args: ['0.0.1-123-123456789'],
    expected: false,
  },
  {
    func: 'getFileHash',
    args: [],
    expected: null,
  },
  {
    func: 'getVersion',
    args: [],
    expected: 6,
  },
  {
    func: 'toCompactObject',
    args: ['0.0.90-1658726417-327924961'],
    expected: v6CompactObject,
  },
  {
    func: 'toCompactObject',
    args: ['0.0.1-123-123456789'],
    expectErr: true,
  },
];

const testRecordFiles = {
  v2: [
    {
      buffer: fs.readFileSync(path.join(v2RecordStreamsPath, '2021-01-26T18_05_00.032280Z.rcd')),
      checks: [
        {
          func: 'containsTransaction',
          args: ['0.0.123128-1611684290-402410035'],
          expected: true,
        },
        {
          func: 'containsTransaction',
          args: ['0.0.1-123-123456789'],
          expected: false,
        },
        {
          func: 'getFileHash',
          args: [],
          expected: Buffer.from('HBbDl2r1C9Uojbe0Gkb9FYrkMt7IYLl+tFpEFmUD1gb2oosKGeHRjO+kCgWIH9sw', 'base64'),
        },
        {
          func: 'getMetadataHash',
          args: [],
          expected: null,
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {
            '0.0.123128-1611684289-109863383-0-false': 0,
            '0.0.123128-1611684290-402410035-0-false': 1,
            '0.0.123128-1611684291-632478870-0-false': 2,
            '0.0.123128-1611684289-402138954-0-false': 3,
            '0.0.123128-1611684291-498208118-0-false': 4,
          },
        },
        {
          func: 'getVersion',
          args: [],
          expected: 2,
        },
        {
          func: 'toCompactObject',
          args: ['0.0.365299-1615141267-266970662'],
          expectErr: true,
        },
      ],
    },
  ],
  v5: [
    {
      buffer: fs.readFileSync(path.join(v5RecordStreamsPath, '2021-03-07T18_21_20.041164000Z.rcd')),
      checks: [
        ...commonV5Checks,
        {
          func: 'getMetadataHash',
          args: [],
          expected: null,
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {
            '0.0.365299-1615141267-604167594-0-false': 0,
            '0.0.365299-1615141267-266970662-0-false': 1,
            '0.0.365299-1615141269-24812188-0-false': 2,
            '0.0.365299-1615141271-650221876-0-false': 3,
            '0.0.365299-1615141267-669386363-0-false': 4,
            '0.0.365299-1615141269-430266882-0-false': 5,
            '0.0.88-1615141270-697933000-0-false': 6,
          },
        },
      ],
    },
    {
      obj: v5CompactObject,
      checks: [
        ...commonV5Checks,
        {
          func: 'getMetadataHash',
          args: [],
          expected: Buffer.from('c0tZ6aeUL5VcHp2h/cCbJf4K5YdSGylCfxo4l1sinKU1e5XUqUKStZL84yBFg41l', 'base64'),
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {'0.0.365299-1615141267-266970662-0-false': null},
        },
      ],
    },
  ],
  v6: [
    {
      buffer: fs.readFileSync(path.join(v6RecordStreamsPath, '2022-07-25T05_20_26.016617003Z.rcd')),
      checks: [
        ...commonV6Checks,
        {
          func: 'getMetadataHash',
          args: [],
          expected: null,
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {
            '0.0.90-1658726413-451096835-0-false': 0,
            '0.0.90-1658726413-169011949-0-false': 1,
            '0.0.90-1658726414-804049281-0-false': 2,
            '0.0.90-1658726413-721724245-0-false': 3,
            '0.0.90-1658726417-275420039-0-false': 4,
            '0.0.90-1658726417-327924961-0-false': 5,
            '0.0.90-1658726416-923370178-0-false': 6,
            '0.0.90-1658726416-683133317-0-false': 7,
            '0.0.90-1658726414-436906280-0-false': 8,
            '0.0.90-1658726415-926052066-0-false': 9,
            '0.0.90-1658726414-63781067-0-false': 10,
            '0.0.90-1658726418-510432850-0-false': 11,
            '0.0.90-1658726418-131669339-0-false': 12,
            '0.0.90-1658726417-797595848-0-false': 13,
            '0.0.90-1658726417-566725327-0-false': 14,
            '0.0.90-1658726414-311613333-0-false': 15,
            '0.0.90-1658726416-691474692-0-false': 16,
          },
        },
      ],
    },
    {
      obj: v6CompactObject,
      checks: [
        ...commonV6Checks,
        {
          func: 'getMetadataHash',
          args: [],
          expected: Buffer.from('jSe8LNhaMESeJAy0LTXoFlvdNsIfBX9yPuhZmpLjDAdCnBkTgVdeDoiktbsGwQSE', 'base64'),
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {'0.0.90-1658726417-327924961-0-false': null},
        },
      ],
    },
  ],
};

const testSignatureFiles = {
  v2: {
    buffer: fs.readFileSync(path.join(v2RecordStreamsPath, '2021-01-26T18_05_00.032280Z.rcd_sig')),
    expected: {
      fileHash: Buffer.from('HBbDl2r1C9Uojbe0Gkb9FYrkMt7IYLl+tFpEFmUD1gb2oosKGeHRjO+kCgWIH9sw', 'base64'),
      fileHashSignature: Buffer.from(
        'UZyy+eKQPYVKtpdtqA4CIRm0L2QhwNhBj3k5Uw8QO82Kp4tv4B2kePRhLQ7dJ6H9epHtxKLywLjOxb+Mt2E7KI2GB1/7rGEfm' +
          'MaO6uqoN4hwkaAHN7Cyu5IH5cVJklHB/RGAIPLKASF7lhrPU4g+FpQZBRJCPe9dqMQFNfiORIioZ3DlS05XS7aLnC5PtWQzg3' +
          'azU5+j30qQva0tiOfqPN57d0c4iqptJGO55/WHSS3FAe4JRdcksm/WLXrxMdGb6JUqGeOKAd2z7eib+HiHrv5gmj1iC5XBZS1' +
          'Nadd7G1QLe404RN2Afeger6eUIvLoaliaUegDh04syaFoXa+ufCuNfV5LeHWSjM+n+5WhKY2D675CTcnY1dwpoF5pcS7yCAOL' +
          'ha7qZqCfKw66JVVOFRL/IkNpWCMkdphEr7BpRypne8oeZfOYFTdYaTllsAhr2YvpgvxFSUG0L3+XeTqhYxBOApzQ+Ew9Ze4Wz' +
          '87rkBv4yzux8aeZ4f2gx364KpM/',
        'base64'
      ),
      version: 2,
    },
  },
  v5: {
    buffer: fs.readFileSync(path.join(v5RecordStreamsPath, '2021-03-07T18_21_20.041164000Z.rcd_sig')),
    expected: {
      fileHash: Buffer.from('67uxdUQ8Q2k/h3NEYLtIoN/Pi4Qh8G30hNOy01uOOEaNdApIF7LG2W+ph8lrnv+j', 'base64'),
      fileHashSignature: Buffer.from(
        'N5oKZ7MiEYM7VP9v9W80jivv175reKdS1KVhkZ25H4PVaAqnZw8ZctNfu/kPP4uOott8MuWVAX+ZQ54SQnsa5fCu0mRmziVxF' +
          '3p+i90IdRqykEzeMd18Mf/tvkxA8SVCGkfL35g6rKg/wnAhpz59nrhZwF1L1wD/i24GNnUcKxaRtRuPKLc0SNnTzh96kKaAu6' +
          'HLk2cODmYOaSkMU9U/k/k9U0a8qwrBP7cvhsvGJL/J+m+na6bdMuYBzRrdF5G+5K3fGGFiEp1TZXOXdWEvH7NqvtHgx3JhtXr' +
          'M2FhBITzaeg9xOeoSG5ZZiO5X6xJ8xg+tq7k9vOIz9CkBcqSIT+zeHKX6ep0GD5vXTbN/7gUVk1jDAZVJ3Z+SzxudTUi3M7/g' +
          'ALzyZ5Y3Uc/DjqaSG4tWlm+1u73GKzz8MLntp+yQratu5atHC2QW10VIGefz8yyr+A31ne8toLi35u+kImEdviqdBsyldtHCs' +
          '25VcC1ZI4vT6IUQHwqEsO7EbX1n',
        'base64'
      ),
      metadataHash: Buffer.from('c0tZ6aeUL5VcHp2h/cCbJf4K5YdSGylCfxo4l1sinKU1e5XUqUKStZL84yBFg41l', 'base64'),
      metadataHashSignature: Buffer.from(
        'Qy4YQ+aZZB+EawoT3Ysc60+uvUQ6jjaCSAXzx3TtHJelkUK5fJW5l0MkqJlxL2M9Naw8qoRfBI210mD7UbXgs9Q3lmDk/FWDF' +
          'OG/F1sTUQvlpteh4DpkaOo2i+l4JL+gOYAZgFGRxNdsK4jrnU2TcJVR6WEQ9p4f6rRWs0q4b+PN9CBqvvXdBgQ0OQB8iQ3YlD' +
          '6LGWlCD98usHKExRUcWyciQTnhg7RVxp/e0qUMj2pa5jv7BbkX3zCSNbPH6F3qdlT44WIAKY/ygygC2hagrJc9yvrwREG36Aj' +
          'UYGcnktaN3ZsQL15XNPP0tAYddiilngRGGF9xuDqnn7gIJc1Ovv3lVwe+77PZpjDhGgQ3+OBf5Y7v2Iy9I0ZBZKuXyVZHvpwa' +
          'qjaIPY2dFzhi0HHL7nyo7tR46uTnYI6sNu8d7KEcFylWmaMVlVUVTp5hceecr7VKrJ7S+wlELDOfoLcwYIzt+iY4j4Tapp9Q8' +
          'dektc9/PkqtiysCM84v7Pvq3tvx',
        'base64'
      ),
      version: 5,
    },
  },
  v6: {
    buffer: fs.readFileSync(path.join(v6RecordStreamsPath, '2022-07-25T05_20_26.016617003Z.rcd_sig')),
    expected: {
      fileHash: Buffer.from('fPXooSulG4JGAWsnXXV4h4trVQjnSICa8LoDkv1bPHEoyfIYUXpiolNU4YSQ8xyB', 'base64'),
      fileHashSignature: Buffer.from(
        'KSjqnJWJZdtSSpREU8NYcaGegRwl/3NrH3tAiiatE7qhGXYpLddTR7j8GGvXZ0VUk5U5ncbFUqVZPZIxMlGLrdl2qMW5OCBIorloEwcc' +
          'V+NN430NnMc/rbOYrC6EuqMIQqtCiwq6gYR3hdAQ3hD+c1w8CpwMz4XtRKE16UvBDDUhZF/gjURwlH2SljgZzkA02lv5Ntb56IIbltq3+cAC' +
          '+ud8O8xOeLXsItMERuR7+bD/I1ujCqGT6xVwR97hii75Ma1ePvb/y7qjmjSCxeyLwQcKW0iEDEjcMkCHAHrcoV+rlDM6I3FwjgGZAKDl2HRJ' +
          'JjW3mhnrMU65g7IJeqXyd+/LNrdnG6a7i1DjIRAUZwiQCSXHFli6bZkoiwuHhm8tbfHvY7F3zX2u2+gEiqSAUJ9/0nCgNc3ELIy0wHFcBV3O' +
          'BxZypioApv3guFk0i5g0Az66s4lnKR7Vakg/eUqyhWg3YkbKuMkpiSAFdR9rpN9an98MyxdilKLAwAX9MHYo',
        'base64'
      ),
      metadataHash: Buffer.from('jSe8LNhaMESeJAy0LTXoFlvdNsIfBX9yPuhZmpLjDAdCnBkTgVdeDoiktbsGwQSE', 'base64'),
      metadataHashSignature: Buffer.from(
        'ObbMCOSkr4uTLL3dOHKhicpKJRx0GAAF+uDg+E6om2pqKjsrJPMYn6Sm5hqyvt+kRZQ/n4RJ4IdJSxikKYkjs6WV6b9UMSFifmN2YxmX' +
          '/jySvTgHt1CVAmtNJnPsEj6VxhlEXrWQHYM26R2Wl4/jEpQkCzUEM0WQ0bQvYcCmvGjYPfrsBD7vV28V3ckkmWyio1XE52n5OMyZVj+NlltO' +
          'UxfFO0PtNXu2USfCaun84OLDrH+/WzLjKo98Qt3NP+1Vp9Q60itZ1tVZyvc8hIlhG0LqGjIraXXwNRccztSGXkOhqkDNA5KA3F6GH5iE3dBM' +
          'OWkDHu/z7Mfkj/nvQS7xtAxslMcIvWDzuoOT5qtzocZBR8hqP6POhmPboTEj4kr1JXLCU0Nku05yfaSchvRxY5sjBqknjuYu2ZoRVaPM5sJ+' +
          'm365wMCMIHsKLTnWGlAWTkni+Cf2m7kR8IHi99XWwY0/qoYjI1214oXydYPTMs/MT7M+OFyUXk0mu0Gj1imM',
        'base64'
      ),
      version: 6,
    },
  },
};

const copyRecordFileAndSetVersion = (buffer, version) => {
  const copy = Buffer.from(buffer);
  copy.writeInt32BE(version);
  return copy;
};

const testRecordFileUnsupportedVersion = (versions, clazz) => {
  const v2Buffer = testRecordFiles.v2[0].buffer;
  const testSpecs = versions.map((version) => [version, copyRecordFileAndSetVersion(v2Buffer, version)]);

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`create object from version ${version}`, () => {
      expect(() => new clazz(buffer)).toThrowErrorMatchingSnapshot();
    });
  });
};

const testRecordFileCanCompact = (testSpecs, clazz) => {
  const v2Buffer = testRecordFiles.v2[0].buffer;
  testSpecs
    .map(([version, expected]) => {
      return {
        version,
        buffer: copyRecordFileAndSetVersion(v2Buffer, version),
        expected,
      };
    })
    .forEach((testSpec) => {
      const {version, buffer, expected} = testSpec;
      test(`version ${version} - ${expected ? 'can compact' : 'cannot compact'}`, () => {
        expect(clazz.canCompact(buffer)).toEqual(expected);
      });
    });
};

const testRecordFileFromBufferOrObj = (version, clazz, supportObj = false, hasRunningHash = false) => {
  const getTestRecordFiles = (ver) => testRecordFiles[`v${ver}`];

  describe('check individual field', () => {
    getTestRecordFiles(version).forEach((testSpec) => {
      const {buffer, obj, checks} = testSpec;
      const bufferOrObj = buffer || obj;
      const name = `from v${version} ${buffer ? 'buffer' : 'compact object'}`;

      checks.forEach((check) => {
        test(`${name} - ${check.func} - ${JSON.stringify(check.args)}`, () => {
          const recordFile = new clazz(bufferOrObj);
          const fn = recordFile[check.func];
          if (!check.expectErr) {
            const actual = fn.apply(recordFile, check.args);
            expect(actual).toEqual(check.expected);
          } else {
            expect(() => fn.apply(recordFile, check.args)).toThrowErrorMatchingSnapshot();
          }
        });
      });
    });
  });

  test(`v${version} buffer with extra data`, () => {
    const buffer = Buffer.concat([getTestRecordFiles(version)[0].buffer, Buffer.from([0])]);
    expect(() => new clazz(buffer)).toThrowErrorMatchingSnapshot();
  });

  test(`truncated v${version} buffer`, () => {
    const {buffer} = getTestRecordFiles(version)[0];
    expect(() => new clazz(buffer.slice(0, buffer.length - 1))).toThrowErrorMatchingSnapshot();
  });

  if (!supportObj) {
    test('from non-Buffer obj', () => {
      expect(() => new clazz({})).toThrowErrorMatchingSnapshot();
    });
  }

  if (hasRunningHash) {
    test('end running hash mismatch', () => {
      // make a shallow copy, change the last byte of the end running hash object
      const obj = {...getTestRecordFiles(version)[1].obj};
      const badEndRunningHashObject = Buffer.from(obj.endRunningHashObject);
      const lastIndex = badEndRunningHashObject.length - 1;
      badEndRunningHashObject[lastIndex] = badEndRunningHashObject[lastIndex] ^ 0xff;
      obj.endRunningHashObject = badEndRunningHashObject;

      expect(() => new clazz(obj)).toThrowErrorMatchingSnapshot();
    });
  }
};

export default {
  testSignatureFiles,
  testRecordFileUnsupportedVersion,
  testRecordFileCanCompact,
  testRecordFileFromBufferOrObj,
};
