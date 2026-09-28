package com.vexelbyte.automatizacion.ia;

// Titulo y texto de la fuente, mas la ficha tecnica del dispositivo cuando la
// hay (nula si no). Son material a procesar, nunca instrucciones para el
// modelo: el prompt de sistema lo deja explicito. En un resumen de analisis
// el texto son los fragmentos de varios medios, cada uno con su nombre.
// coberturaOtrosMedios son fragmentos de otros medios sobre la misma noticia
// para ampliar el articulo; nula si la busqueda no esta disponible.
public record SolicitudGeneracionContenido(
    String tituloOriginal,
    String resumenOriginal,
    String fichaTecnica,
    boolean esResumenDeAnalisis,
    String coberturaOtrosMedios) {
}
