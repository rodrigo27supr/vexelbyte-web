-- Noticias del feed que la IA descarto por tematica. Antes vivian en memoria,
-- pero Render gratuito duerme y arranca de cero en cada ciclo: sin esta tabla
-- el job volvia a gastar cuota de IA en las mismas noticias fuera de linea
-- editorial, y si eran las mas recientes ocupaban siempre el cupo del ciclo.
-- VARCHAR(1000) porque el guid de algunos feeds (GSMArena) es la URL completa.
CREATE TABLE noticias_descartadas (
    id_externo_fuente  VARCHAR(1000) PRIMARY KEY,
    motivo             VARCHAR(500) NOT NULL,
    fecha_descarte     TIMESTAMPTZ NOT NULL
);
