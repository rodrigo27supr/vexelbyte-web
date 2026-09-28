-- El build de Vercel fallaba al descargar las fotos de Wikimedia: el backend
-- las descarga una vez con un User-Agent identificable y las sirve el mismo,
-- asi el despliegue solo depende de nuestra API. Tabla aparte para que el
-- listado de articulos no cargue los bytes de cada foto.
CREATE TABLE fotos_articulos (
    articulo_id     BIGINT PRIMARY KEY REFERENCES articulos(id) ON DELETE CASCADE,
    contenido       BYTEA NOT NULL,
    tipo_contenido  VARCHAR(50) NOT NULL
);

-- Las fotos asignadas con V7 apuntaban a Wikimedia y no tienen bytes: vuelven
-- a revisarse para descargarlas.
UPDATE articulos
SET foto_url = NULL,
    foto_ancho = NULL,
    foto_alto = NULL,
    foto_autor = NULL,
    foto_licencia = NULL,
    foto_url_licencia = NULL,
    foto_url_origen = NULL,
    foto_revisada = FALSE
WHERE foto_url IS NOT NULL;
