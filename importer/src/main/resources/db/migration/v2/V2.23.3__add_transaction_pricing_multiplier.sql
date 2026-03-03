alter table if exists transaction
  add column if not exists congestion_pricing_multiplier bigint null,
  add column if not exists high_volume_pricing_multiplier bigint null;
