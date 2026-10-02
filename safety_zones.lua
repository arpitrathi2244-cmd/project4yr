-- SafeRoad: import only the OSM safety features needed by the project.
-- osm2pgsql 2.x Flex output configuration.

local safety_zones = osm2pgsql.define_table({
    name = 'osm_safety_zones',
    ids = { type = 'any', id_column = 'osm_id', type_column = 'osm_type', create_index = 'always' },
    columns = {
        { column = 'name', type = 'text' },
        { column = 'zone_type', type = 'text' },
        { column = 'source', type = 'text' },
        { column = 'source_type', type = 'text' },
        { column = 'source_id', type = 'int8' },
        { column = 'tags', type = 'jsonb' },
        { column = 'geom', type = 'geometry', projection = 4326 }
    }
})

local function classify(tags)
    if tags.amenity == 'school' then
        return 'SCHOOL'
    end

    local natural = tags.natural
    if natural == 'hill' or natural == 'peak' or natural == 'mountain' or natural == 'cliff' then
        return 'HILL_AREA'
    end

    if tags.leisure == 'nature_reserve'
       or tags.boundary == 'protected_area'
       or tags.boundary == 'national_park' then
        return 'WILDLIFE'
    end

    return nil
end

local function add_object(object, geometry)
    if not geometry or geometry:is_null() then return end

    local zone_type = classify(object.tags)
    if not zone_type then return end

    safety_zones:insert({
        name = object.tags.name or object.tags['name:en'] or object.tags['official_name'] or zone_type,
        zone_type = zone_type,
        source = 'OPENSTREETMAP',
        source_type = object.type,
        source_id = object.id,
        tags = object.tags,
        geom = geometry
    })
end

function osm2pgsql.process_node(object)
    local zone_type = classify(object.tags)
    if not zone_type then return end
    add_object(object, object:as_point())
end

function osm2pgsql.process_way(object)
    local zone_type = classify(object.tags)
    if not zone_type then return end

    -- Handles closed schools/protected areas and linear hill/cliff features.
    add_object(object, object:as_multipolygon())
end

function osm2pgsql.process_relation(object)
    local zone_type = classify(object.tags)
    if not zone_type then return end

    -- Protected areas and national parks are commonly multipolygon relations.
    add_object(object, object:as_multipolygon())
end
