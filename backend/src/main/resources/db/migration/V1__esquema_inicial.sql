-- Esquema preliminar de VexelByte (ROADMAP_VEXELBYTE.md, Fase 1.3).
-- Sujeto a evolucion via nuevas migraciones Flyway; este archivo, una vez
-- aplicado en un entorno, no se modifica retroactivamente.

CREATE TABLE juegos (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(255) NOT NULL,
    desarrollador     VARCHAR(255),
    distribuidor      VARCHAR(255),
    fecha_lanzamiento DATE,
    id_externo        VARCHAR(100) UNIQUE,
    caratula_url      VARCHAR(500)
);

CREATE TABLE juego_plataformas (
    juego_id   BIGINT NOT NULL REFERENCES juegos (id) ON DELETE CASCADE,
    plataforma VARCHAR(100) NOT NULL
);

CREATE TABLE articulos (
    id                    BIGSERIAL PRIMARY KEY,
    tipo                  VARCHAR(20) NOT NULL,
    titulo                VARCHAR(255) NOT NULL,
    descripcion           VARCHAR(500) NOT NULL,
    slug                  VARCHAR(255) NOT NULL UNIQUE,
    cuerpo_html           TEXT NOT NULL,
    fecha_publicacion     TIMESTAMPTZ NOT NULL,
    fecha_actualizacion   TIMESTAMPTZ,
    autor                 VARCHAR(255) NOT NULL,
    imagen_destacada      VARCHAR(500),
    -- Nace en borrador: la publicacion automatica sin revision editorial
    -- queda descartada por ahora (ROADMAP_VEXELBYTE.md, decision abierta #2).
    borrador              BOOLEAN NOT NULL DEFAULT TRUE,
    juego_relacionado_id  BIGINT REFERENCES juegos (id),
    puntuacion            NUMERIC(3, 1) CHECK (puntuacion IS NULL OR (puntuacion >= 0 AND puntuacion <= 10))
);

CREATE INDEX idx_articulos_slug ON articulos (slug);
CREATE INDEX idx_articulos_tipo ON articulos (tipo);

CREATE TABLE componentes_hardware (
    id                     BIGSERIAL PRIMARY KEY,
    nombre_producto        VARCHAR(255) NOT NULL,
    categoria              VARCHAR(100) NOT NULL,
    fabricante             VARCHAR(255),
    especificaciones_json  JSONB,
    precio_referencia      NUMERIC(10, 2),
    imagen_destacada       VARCHAR(500)
);

CREATE INDEX idx_componentes_hardware_categoria ON componentes_hardware (categoria);

CREATE TABLE enlaces_afiliado (
    id                        BIGSERIAL PRIMARY KEY,
    proveedor                 VARCHAR(100) NOT NULL,
    url_destino               VARCHAR(1000) NOT NULL,
    codigo_seguimiento        VARCHAR(255),
    entidad_relacionada_tipo  VARCHAR(30) NOT NULL,
    entidad_relacionada_id    BIGINT NOT NULL,
    comision_estimada         NUMERIC(10, 2)
);

CREATE INDEX idx_enlaces_afiliado_entidad ON enlaces_afiliado (entidad_relacionada_tipo, entidad_relacionada_id);
