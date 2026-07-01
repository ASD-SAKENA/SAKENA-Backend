CREATE TABLE buildings (
    id         UUID         NOT NULL,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_buildings PRIMARY KEY (id)
);

CREATE TABLE apartments (
    id                 UUID          NOT NULL,
    building_id        UUID          NOT NULL,
    unit_number        VARCHAR(50)   NOT NULL,
    floor_number       INTEGER       NOT NULL,
    area_square_meters NUMERIC(10, 2) NOT NULL,
    bedrooms           INTEGER       NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_apartments PRIMARY KEY (id),
    CONSTRAINT fk_apartments_building FOREIGN KEY (building_id) REFERENCES buildings (id),
    CONSTRAINT uk_apartments_building_unit UNIQUE (building_id, unit_number),
    CONSTRAINT ck_apartments_floor_non_negative CHECK (floor_number >= 0),
    CONSTRAINT ck_apartments_area_positive CHECK (area_square_meters > 0),
    CONSTRAINT ck_apartments_bedrooms_range CHECK (bedrooms >= 0 AND bedrooms <= 20)
);

CREATE INDEX idx_apartments_building_id ON apartments (building_id);
