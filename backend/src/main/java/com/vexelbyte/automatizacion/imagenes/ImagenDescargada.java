package com.vexelbyte.automatizacion.imagenes;

// Dimensiones leidas de los propios bytes: Astro las necesita para reservar
// el hueco de la imagen y que no haya saltos de maquetacion (CLS).
public record ImagenDescargada(byte[] contenido, String tipoContenido, int ancho, int alto) {
}
