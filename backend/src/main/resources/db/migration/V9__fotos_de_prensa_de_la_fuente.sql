-- Las fotos de Wikimedia Commons eran pocas y de aficionado (un movil en una
-- mano cualquiera): se sustituyen por la imagen de prensa del fabricante que
-- trae la noticia de origen. Para localizarla hace falta el enlace a esa
-- noticia, que hasta ahora solo estaba en el credito del cuerpo.
ALTER TABLE articulos ADD COLUMN enlace_fuente VARCHAR(1000);

-- El credito es "Fuente original: <a href=...>" o "Fuentes: <a href=...>", y el
-- primer enlace es siempre la pieza que origina el articulo.
UPDATE articulos
SET enlace_fuente = substring(cuerpo_html from 'Fuentes?[^:<]*: <a href="([^"]+)"');

DELETE FROM fotos_articulos;

UPDATE articulos
SET foto_url = NULL,
    foto_ancho = NULL,
    foto_alto = NULL,
    foto_autor = NULL,
    foto_licencia = NULL,
    foto_url_licencia = NULL,
    foto_url_origen = NULL,
    foto_revisada = FALSE;
