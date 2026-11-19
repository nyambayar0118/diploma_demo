-- ХШ08: after account deletion routes stay as anonymous evaluation data (no owner, no start point).
ALTER TABLE routes ALTER COLUMN owner_id DROP NOT NULL;
ALTER TABLE routes ALTER COLUMN start_location DROP NOT NULL;
ALTER TABLE routes DROP CONSTRAINT routes_owner_id_fkey;
ALTER TABLE routes ADD CONSTRAINT routes_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL;
