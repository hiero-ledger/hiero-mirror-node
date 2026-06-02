-- SPDX-License-Identifier: Apache-2.0

-- Tracks progress of chunked pending reward calculation per staking period (epoch day).
-- This allows the importer to resume after restart without reprocessing already completed chunks.

create table if not exists entity_stake_calculation_state (
  end_stake_period bigint primary key,
  last_entity_id   bigint not null,
  completed        boolean not null default false,
  updated_at       timestamptz not null default now()
);

-- Pre-computed proxy staking totals for the current staging period. Populated once per period
-- alongside entity_state_start and read by every updateEntityStakeChunk call instead of
-- re-aggregating entity_state_start on every chunk.
create table if not exists entity_state_proxy_staking (
  staked_account_id bigint primary key,
  staked_to_me      bigint not null
);

