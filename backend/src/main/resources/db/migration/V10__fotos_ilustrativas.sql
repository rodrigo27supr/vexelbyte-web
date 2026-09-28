-- Todo articulo lleva foto: si la noticia de origen no trae material oficial,
-- una foto ilustrativa de Pexels (licencia libre). El pie de foto la presenta
-- como ilustrativa para que nadie crea que es el producto exacto.
ALTER TABLE articulos ADD COLUMN foto_es_ilustrativa BOOLEAN NOT NULL DEFAULT FALSE;

-- Los articulos revisados sin foto oficial vuelven a revisarse para recibir
-- su foto ilustrativa.
UPDATE articulos SET foto_revisada = FALSE WHERE foto_url IS NULL;
