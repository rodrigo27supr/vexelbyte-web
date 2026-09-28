package com.vexelbyte.automatizacion.persistencia;

import java.time.OffsetDateTime;

// Todo lo que TrabajoSincronizacionContenido ya conoce de la fuente oficial
// (el feed RSS) antes de llamar a la IA -- lo separo del cuerpoHtml aprobado
// porque ese viaja como parametro propio en
// ServicioIngestaArticulos.ingestarArticulo: quiero que sea imposible
// construir este registro con contenido que todavia no paso la validacion
// de PipelineGeneracionContenido.
public record DatosFuenteArticulo(
    String idExternoFuente,
    String enlaceFuente,
    String titulo,
    String descripcion,
    OffsetDateTime fechaPublicacion) {
}
