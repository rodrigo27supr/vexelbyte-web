package com.vexelbyte.automatizacion.rss;

import java.time.Instant;

// DTO publico del cliente de feed -- desacopla al resto del motor del XML
// crudo. resumen ya llega en texto plano, sin el HTML que meten algunos feeds.
public record NoticiaHardwareRss(String identificadorExterno, String titulo, String enlaceOriginal, String resumen,
    Instant fechaPublicacion) {
}
