-- Decision del Tech Lead (2026-09-27): un articulo de cada seccion cada dos
-- dias. Para saber cuando publico VexelByte en cada seccion hace falta la
-- fecha de alta propia: fecha_publicacion es la de la fuente, y una noticia de
-- hace tres dias publicada hoy dejaria la seccion abierta otra vez al momento.
ALTER TABLE articulos ADD COLUMN fecha_creacion TIMESTAMPTZ;

-- Los articulos existentes toman la fecha de su fuente, la mejor aproximacion
-- disponible a cuando se publicaron aqui.
UPDATE articulos SET fecha_creacion = fecha_publicacion;

ALTER TABLE articulos ALTER COLUMN fecha_creacion SET NOT NULL;

-- La retencion borra por antiguedad (fotos al ano y medio, articulos a los
-- diez anos, descartes al mes): indices para que esas consultas no recorran
-- las tablas enteras cuando crezcan.
CREATE INDEX idx_articulos_categoria_fecha_creacion ON articulos (categoria, fecha_creacion);
CREATE INDEX idx_articulos_fecha_publicacion ON articulos (fecha_publicacion);
CREATE INDEX idx_noticias_descartadas_fecha ON noticias_descartadas (fecha_descarte);
