package com.vexelbyte.automatizacion.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vexelbyte.articulos.EspecificacionTecnica;
import java.util.List;

// Forma del JSON que le exijo al modelo en el prompt de sistema (ver
// PipelineGeneracionContenido.PROMPT_SISTEMA). esTematicaHardware es la
// compuerta editorial: el modelo decide primero si la noticia trata de
// hardware y, si no, devuelve false con un motivo en vez de redactar.
// Es Boolean (no boolean) para distinguir un false explicito de un campo
// ausente, que tambien trato como rechazo.
@JsonIgnoreProperties(ignoreUnknown = true)
record ArticuloGeneradoPorIA(
    Boolean esTematicaHardware,
    String motivoRechazo,
    String titulo,
    String entradilla,
    String categoria,
    String producto,
    List<EspecificacionTecnica> especificaciones,
    List<String> puntosClave,
    String cuerpoHtml) {
}
