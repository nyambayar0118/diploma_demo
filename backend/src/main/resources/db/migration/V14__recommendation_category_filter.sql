-- The categories the user limited a recommendation request to (empty = all). Kept with the batch because
-- a filtered list says less about the user's interests (relevant when building training data).
ALTER TABLE recommendation_batches ADD COLUMN category_filter TEXT[] NOT NULL DEFAULT '{}';
