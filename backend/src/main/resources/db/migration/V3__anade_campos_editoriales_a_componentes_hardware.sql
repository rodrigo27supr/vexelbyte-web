-- Cierro la deuda tecnica anotada en ROADMAP_VEXELBYTE.md (Fase 4.1): hardware
-- nacio en V1 como catalogo puro (nombre, categoria, fabricante,
-- especificaciones, precio) porque en ese momento las fichas vivian como
-- content collection en el frontend, sin backend real detras. Ahora que
-- HardwareController pasa a ser la fuente real (mismo patron que articulos),
-- necesito los mismos campos editoriales que ya tiene esa tabla: descripcion,
-- slug para el routing, cuerpo, fecha de publicacion/autor y cola de
-- revision. No hay datos reales sembrados todavia en ningun entorno, asi que
-- puedo anadir estas columnas como NOT NULL sin necesidad de un backfill.
ALTER TABLE componentes_hardware ADD COLUMN descripcion VARCHAR(500) NOT NULL;
ALTER TABLE componentes_hardware ADD COLUMN slug VARCHAR(255) NOT NULL UNIQUE;
ALTER TABLE componentes_hardware ADD COLUMN cuerpo_html TEXT NOT NULL;
ALTER TABLE componentes_hardware ADD COLUMN fecha_publicacion TIMESTAMPTZ NOT NULL;
ALTER TABLE componentes_hardware ADD COLUMN fecha_actualizacion TIMESTAMPTZ;
ALTER TABLE componentes_hardware ADD COLUMN autor VARCHAR(255) NOT NULL;
-- Misma cola de revision editorial que articulos.borrador: nace en TRUE,
-- nunca visible por la API de lectura publica hasta publicarse a proposito.
ALTER TABLE componentes_hardware ADD COLUMN borrador BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_componentes_hardware_slug ON componentes_hardware (slug);
