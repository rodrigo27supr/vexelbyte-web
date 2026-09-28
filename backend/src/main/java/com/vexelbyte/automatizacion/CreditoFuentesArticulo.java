package com.vexelbyte.automatizacion;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

// El articulo lo redacta la IA a partir de piezas ajenas: cito siempre los
// medios y enlazo a cada pieza, tanto al publicar como al reescribir. nofollow
// porque es una cita, no una recomendacion; solo acepto http(s) para no
// inyectar un javascript: del feed.
final class CreditoFuentesArticulo {

  private static final String PLANTILLA_ENLACE_FUENTE =
      "<a href=\"%s\" rel=\"nofollow noopener noreferrer\">%s</a>";

  private CreditoFuentesArticulo() {
  }

  static String anadirCreditoFuentes(String cuerpoHtml, List<String> enlacesFuentes) {
    List<String> enlacesCitables = enlacesFuentes.stream()
        .map(enlace -> extraerDominioSiEsEnlaceWeb(enlace)
            .map(dominio -> PLANTILLA_ENLACE_FUENTE.formatted(HtmlUtils.htmlEscape(enlace), HtmlUtils.htmlEscape(dominio))))
        .flatMap(Optional::stream)
        .toList();
    if (enlacesCitables.isEmpty()) {
      return cuerpoHtml;
    }
    String etiqueta = enlacesCitables.size() == 1 ? "Fuente original: " : "Fuentes: ";
    return cuerpoHtml + "<p>" + etiqueta + String.join(", ", enlacesCitables) + "</p>";
  }

  static Optional<String> extraerDominioSiEsEnlaceWeb(String enlace) {
    try {
      URI uriEnlace = new URI(enlace);
      String esquema = uriEnlace.getScheme();
      boolean esEnlaceWeb = "https".equalsIgnoreCase(esquema) || "http".equalsIgnoreCase(esquema);
      if (!esEnlaceWeb || uriEnlace.getHost() == null) {
        return Optional.empty();
      }
      return Optional.of(uriEnlace.getHost().replaceFirst("^www\\.", ""));
    } catch (URISyntaxException enlaceMalformado) {
      return Optional.empty();
    }
  }
}
