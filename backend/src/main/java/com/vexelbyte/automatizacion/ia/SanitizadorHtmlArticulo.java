package com.vexelbyte.automatizacion.ia;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

// El cuerpo lo escribe un modelo de IA a partir de texto de terceros (el feed
// RSS), y el frontend lo inserta con set:html: una inyeccion de prompt podria
// colar <script>, manejadores on* o enlaces javascript:. Limpio con una lista
// blanca estricta antes de aprobar el contenido, nunca con una lista negra.
@Component
public class SanitizadorHtmlArticulo {

  // Tablas incluidas a proposito: comparativas y benchmarks son el contenido
  // natural de VexelByte. Sin img ni iframe: no quiero cargar recursos ajenos.
  private static final Safelist ETIQUETAS_PERMITIDAS = Safelist.none()
      .addTags("article", "section", "h2", "h3", "h4", "p", "br", "ul", "ol", "li", "strong", "em", "b", "i",
          "blockquote", "code", "table", "thead", "tbody", "tr", "th", "td", "a")
      .addAttributes("a", "href")
      .addProtocols("a", "href", "http", "https")
      .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

  private static final Document.OutputSettings SALIDA_COMPACTA = new Document.OutputSettings().prettyPrint(false);

  public String limpiarHtml(String htmlNoConfiable) {
    return Jsoup.clean(htmlNoConfiable, "", ETIQUETAS_PERMITIDAS, SALIDA_COMPACTA);
  }

  public boolean tieneTextoVisible(String htmlLimpio) {
    return !Jsoup.parse(htmlLimpio).text().isBlank();
  }

  public int contarCaracteresVisibles(String htmlLimpio) {
    return Jsoup.parse(htmlLimpio).text().length();
  }
}
