-- osm2pgsql-ийн flex загвар: системд хэрэгтэй OSM объектуудыг "osm" завсрын схемд ачаална.
-- Ангилалд харгалзуулах ажлыг энд ХИЙХГҮЙ; data/sql/load_places.sql шошгыг osm_tag_mappings хүснэгтээр
-- ангилалд харгалзуулдаг тул osm2pgsql-ийг дахин ажиллуулалгүйгээр ангиллыг өөрчилж болно.

local POI_KEYS = { 'amenity', 'shop', 'leisure', 'tourism', 'historic', 'natural' }

local pois = osm2pgsql.define_table({
    name = 'pois',
    schema = 'osm',
    ids = { type = 'any', type_column = 'osm_type', id_column = 'osm_id' },
    columns = {
        { column = 'tags', type = 'jsonb' },
        { column = 'location', type = 'point', projection = 4326, not_null = true },
        { column = 'area', type = 'multipolygon', projection = 4326 },
    },
})

local buildings = osm2pgsql.define_table({
    name = 'buildings',
    schema = 'osm',
    ids = { type = 'any', type_column = 'osm_type', id_column = 'osm_id' },
    columns = {
        { column = 'tags', type = 'jsonb' },
        { column = 'location', type = 'point', projection = 4326, not_null = true },
        { column = 'area', type = 'multipolygon', projection = 4326, not_null = true },
    },
})

local function is_poi(tags)
    if not (tags.name or tags['name:mn']) then
        return false -- нэргүй объект газар болоход хэрэггүй
    end
    for _, key in ipairs(POI_KEYS) do
        if tags[key] then return true end
    end
    return false
end

function osm2pgsql.process_node(object)
    if is_poi(object.tags) then
        pois:insert({ tags = object.tags, location = object:as_point() })
    end
end

local function process_area(object, geom)
    if geom:is_null() then return end
    if is_poi(object.tags) then
        pois:insert({ tags = object.tags, location = geom:pole_of_inaccessibility(), area = geom })
    end
    if object.tags.building and object.tags.building ~= 'no' then
        buildings:insert({ tags = object.tags, location = geom:pole_of_inaccessibility(), area = geom })
    end
end

function osm2pgsql.process_way(object)
    if object.is_closed and #object.nodes > 3 then
        process_area(object, object:as_polygon())
    end
end

function osm2pgsql.process_relation(object)
    if object.tags.type == 'multipolygon' then
        process_area(object, object:as_multipolygon())
    end
end
