-- Retiro las secciones que nunca llegaron a tener contenido: analisis de
-- juegos, catalogo de hardware y enlaces de afiliado, junto con la tabla de
-- juegos que solo servia a los analisis. Comprobe antes en Neon que todas
-- estaban vacias y que ningun articulo usaba tipo, puntuacion, juego
-- relacionado ni imagen destacada (las portadas son generadas).
ALTER TABLE articulos DROP COLUMN juego_relacionado_id;
ALTER TABLE articulos DROP COLUMN puntuacion;
ALTER TABLE articulos DROP COLUMN imagen_destacada;
-- Con un solo tipo de contenido la columna no distingue nada; su indice
-- idx_articulos_tipo cae con ella.
ALTER TABLE articulos DROP COLUMN tipo;

DROP TABLE enlaces_afiliado;
DROP TABLE componentes_hardware;
DROP TABLE juego_plataformas;
DROP TABLE juegos;
