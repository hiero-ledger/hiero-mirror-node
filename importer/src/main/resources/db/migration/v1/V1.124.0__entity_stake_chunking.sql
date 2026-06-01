-- SPDX-License-Identifier: Apache-2.0

-- Tracks progress of chunked pending reward calculation per staking period (epoch day).
-- This allows the importer to resume after restart without reprocessing already completed chunks.

create table if not exists entity_stake_calculation_state (
  end_stake_period bigint primary key,
  last_entity_id   bigint not null,
  completed        boolean not null default false,
  updated_at       timestamptz not null default now()
);

create index if not exists entity_stake_calculation_state__completed on entity_stake_calculation_state (completed);

