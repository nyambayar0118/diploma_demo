-- Minu Map (map.minu.mn) as a second place source next to OpenStreetMap.
-- Places only Minu knows get source = 'MINU'; OSM places Minu also lists keep source 'OSM'
-- and are linked through minu_id so a re-import updates instead of duplicating.

ALTER TABLE places DROP CONSTRAINT IF EXISTS places_source_check;
ALTER TABLE places ADD CONSTRAINT places_source_check CHECK (source IN ('OSM', 'USER', 'MINU'));

ALTER TABLE places ADD COLUMN minu_id   VARCHAR(30);
ALTER TABLE places ADD COLUMN minu_data JSONB;          -- the original Minu record, for later features
CREATE UNIQUE INDEX places_minu_id_unique ON places (minu_id) WHERE minu_id IS NOT NULL;

-- Minu category (English name, as in categoryNameEng) -> our category.
-- Patterns are case-insensitive regular expressions; the highest priority wins per company.
CREATE TABLE minu_category_mappings (
    id          BIGSERIAL PRIMARY KEY,
    pattern     VARCHAR(200) NOT NULL,
    category_id BIGINT       NOT NULL REFERENCES place_categories (id),
    priority    INT          NOT NULL DEFAULT 10
);

INSERT INTO minu_category_mappings (pattern, category_id, priority)
SELECT m.pattern, c.id, m.priority
FROM (VALUES
    ('coffee|cafe|tea house',                          'cafe',        40),
    ('fast food|burger|pizza|hot ?dog|kebab|shawarma', 'fast_food',   45),
    ('restaurant|^food$|cuisine|canteen|buuz|hot ?pot', 'restaurant', 30),
    ('\mpub\M|\mbars?\M|lounge|beer|wine',             'pub_bar',     40),
    ('bakery|cake|dessert|confection|ice cream|pastr', 'dessert',     42),
    ('cinema|movie',                                   'cinema',      50),
    ('game|bowling|billiard|escape|playstation|\mpc\M|internet cafe|arcade', 'game_center', 45),
    ('karaoke',                                        'karaoke',     55),
    ('night ?club|disco',                              'nightclub',   55),
    ('amusement|water park|theme park|attraction park','theme_park',  50),
    ('\mzoo\M|aquarium',                               'zoo',         50),
    ('museum',                                         'museum',      50),
    ('gallery|exhibition',                             'gallery',     48),
    ('theat|concert|opera|philharmon|circus',          'theatre',     50),
    ('librar',                                         'library',     50),
    ('monument|memorial|historic',                     'historic',    45),
    ('temple|monaster|church|mosque|worship',          'worship',     50),
    ('\mparks?\M|garden',                              'park',        35),
    ('camp|resort',                                    'picnic',      30),
    ('fitness|\mgym\M|yoga',                           'fitness',     45),
    ('sport|stadium|arena|tennis|basketball',          'sports_hall', 35),
    ('swimming|\mpool\M',                              'swimming',    48),
    ('ice rink|skating',                               'ice_rink',    50),
    ('shopping (center|centre|mall)|\mmall\M|department store|trade cent', 'mall', 40),
    ('\mmarkets?\M|bazaar',                            'market',      35),
    ('book ?(shop|store)|bookstore',                   'bookshop',    40),
    ('^(clothing|clothes|shoes?|fashion|boutiques?)( store| shop)?s?$', 'fashion', 30),
    ('gift|souvenir|handicraft|craft',                 'gift',        35),
    ('\mspa\M|sauna|massage|bath ?house|public bath', 'spa',         45),
    ('hotel|hostel|guest ?house',                      'lodging',     20)
) AS m(pattern, code, priority)
JOIN place_categories c ON c.code = m.code;
