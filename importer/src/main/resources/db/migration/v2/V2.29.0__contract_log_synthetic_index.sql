-- SPDX-License-Identifier: Apache-2.0

create index if not exists contract_log__synthetic_timestamp
    on contract_log (consensus_timestamp desc, index desc)
    where synthetic is true;
