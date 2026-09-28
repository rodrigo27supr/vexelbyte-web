-- Fase 2.3: necesito una clave de deduplicacion propia del articulo (no del
-- juego) para que el job de ingesta pueda comprobar idempotencia antes de
-- insertar. Guardo aqui el identificador de la noticia en su fuente original
-- (el "gid" de Steam) -- distinto de juegos.id_externo, que es el AppID del
-- juego, no de la noticia individual.
ALTER TABLE articulos ADD COLUMN id_externo_fuente VARCHAR(255) UNIQUE;
