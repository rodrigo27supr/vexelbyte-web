-- Fase 6: datos editoriales estructurados que redacta la IA junto al cuerpo.
-- Todo nullable: los articulos anteriores no los tienen y el frontend los
-- muestra solo cuando existen.
--   categoria: linea editorial (HARDWARE_PC, MOVILES, PERIFERICOS, RENDIMIENTO).
--   producto: nombre del producto principal, para la portada generada.
--   especificaciones_json: ficha tecnica como lista de {nombre, valor}.
--   puntos_clave_json: 3-5 frases de resumen como lista de textos.
ALTER TABLE articulos ADD COLUMN categoria VARCHAR(30);
ALTER TABLE articulos ADD COLUMN producto VARCHAR(150);
ALTER TABLE articulos ADD COLUMN especificaciones_json JSONB;
ALTER TABLE articulos ADD COLUMN puntos_clave_json JSONB;

CREATE INDEX idx_articulos_categoria ON articulos (categoria);
