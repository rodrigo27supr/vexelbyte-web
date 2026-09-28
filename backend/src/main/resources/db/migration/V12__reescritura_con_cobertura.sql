-- Decision del Tech Lead (2026-09-27): los articulos redactados solo con su
-- fuente se reescriben con la cobertura de otros medios. El ciclo los procesa
-- poco a poco para no agotar la cuota de la IA ni la de busqueda; los nuevos ya
-- nacen con cobertura y quedan revisados.
ALTER TABLE articulos ADD COLUMN redaccion_revisada BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE articulos SET redaccion_revisada = FALSE;
