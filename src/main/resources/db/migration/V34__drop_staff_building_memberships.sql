-- Reverses V32. Scoping staff to a building was wrong: service staff are a
-- shared pool of accounts that take work from several buildings, and they
-- belong to none of them. V32 is left in place because it has already been
-- applied — Flyway never rewrites history.
DROP TABLE IF EXISTS staff_building_memberships;
