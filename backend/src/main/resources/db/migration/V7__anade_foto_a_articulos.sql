-- Foto real del producto desde Wikimedia Commons, solo con licencia libre.
-- Autor, licencia y fichero de origen son obligatorios para citarla: sin
-- ellos no se puede mostrar. foto_revisada evita repetir la busqueda en cada
-- ciclo cuando Commons no tiene foto del producto.
ALTER TABLE articulos ADD COLUMN foto_url VARCHAR(1000);
ALTER TABLE articulos ADD COLUMN foto_ancho INTEGER;
ALTER TABLE articulos ADD COLUMN foto_alto INTEGER;
ALTER TABLE articulos ADD COLUMN foto_autor VARCHAR(300);
ALTER TABLE articulos ADD COLUMN foto_licencia VARCHAR(100);
ALTER TABLE articulos ADD COLUMN foto_url_licencia VARCHAR(500);
ALTER TABLE articulos ADD COLUMN foto_url_origen VARCHAR(1000);
ALTER TABLE articulos ADD COLUMN foto_revisada BOOLEAN NOT NULL DEFAULT FALSE;
