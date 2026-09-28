package com.vexelbyte.automatizacion.gsmarena;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// El RSS de GSMArena solo trae un extracto de unos 600 caracteres, y en sus
// analisis ninguno. Descargo la pagina de la noticia para darle a la IA el
// texto completo y, si enlaza un dispositivo, su ficha tecnica (pantalla,
// chip, camaras, bateria...). Cuanto mas dato real recibe, menos rellena.
@Component
public class ClienteNoticiaGsmarena {

  private static final String HOST_GSMARENA = "www.gsmarena.com";

  // Una ficha de dispositivo es "marca_modelo-12345.php"; las noticias y
  // analisis llevan "-news-" o "-review-" y no son fichas.
  private static final Pattern ENLACE_FICHA_DISPOSITIVO = Pattern.compile("^[a-z0-9_]+-\\d+\\.php$");

  // Suficiente para las secciones utiles; las bandas de red y el resto de
  // la ficha solo engordarian el prompt.
  private static final int LONGITUD_MAXIMA_FICHA = 3500;

  // Mismo tope que el resumen del RSS: las noticias de GSMArena rondan los
  // 1.500 caracteres, asi que solo recorta paginas anomalas.
  private static final int LONGITUD_MAXIMA_TEXTO_CUERPO = 4000;

  private final RestClient clienteHttp;

  public ClienteNoticiaGsmarena(@Qualifier("clienteHttpFeedHardware") RestClient clienteHttp) {
    this.clienteHttp = clienteHttp;
  }

  public boolean esNoticiaDeGsmarena(String enlaceNoticia) {
    try {
      return HOST_GSMARENA.equalsIgnoreCase(URI.create(enlaceNoticia).getHost());
    } catch (IllegalArgumentException enlaceMalformado) {
      return false;
    }
  }

  // Sus analisis reparten las pruebas en seis o siete paginas y el RSS no trae
  // ni una linea: con la primera pagina solo tendriamos la introduccion.
  public boolean esAnalisisDeGsmarena(String enlaceNoticia) {
    return esNoticiaDeGsmarena(enlaceNoticia) && enlaceNoticia.contains("-review-");
  }

  public MaterialNoticiaGsmarena obtenerMaterial(String enlaceNoticia) {
    Document paginaNoticia = descargarPagina(enlaceNoticia);
    return new MaterialNoticiaGsmarena(extraerTextoCuerpo(paginaNoticia), obtenerFichaTecnica(paginaNoticia));
  }

  private Document descargarPagina(String url) {
    String html = clienteHttp.get().uri(url).retrieve().body(String.class);
    return Jsoup.parse(html == null ? "" : html, url);
  }

  // Los comentarios de los lectores quedan fuera de #review-body, asi que no
  // se cuelan como si fueran parte de la noticia.
  private String extraerTextoCuerpo(Document paginaNoticia) {
    String textoCuerpo = paginaNoticia.select("#review-body p").stream()
        .map(Element::text)
        .filter(parrafo -> !parrafo.isBlank())
        .collect(Collectors.joining(" "));
    return textoCuerpo.length() > LONGITUD_MAXIMA_TEXTO_CUERPO
        ? textoCuerpo.substring(0, LONGITUD_MAXIMA_TEXTO_CUERPO)
        : textoCuerpo;
  }

  // Nula si la noticia no enlaza ningun dispositivo (una noticia de apps, un
  // accesorio sin ficha): el articulo se redacta igual, solo con el texto.
  private String obtenerFichaTecnica(Document paginaNoticia) {
    Optional<String> enlaceFicha = buscarEnlaceFichaEnCuerpo(paginaNoticia);
    if (enlaceFicha.isEmpty()) {
      return null;
    }
    String ficha = extraerFicha(descargarPagina(enlaceFicha.get()));
    return ficha.isBlank() ? null : ficha;
  }

  // Solo busco dentro de los parrafos del cuerpo: la barra lateral de
  // "tendencias" enlaza siempre los mismos moviles y confundiria el dispositivo.
  // Devuelvo la URL absoluta resuelta contra la de la propia noticia.
  private Optional<String> buscarEnlaceFichaEnCuerpo(Document paginaNoticia) {
    return paginaNoticia.select("#review-body p a[href]").stream()
        .filter(enlace -> esEnlaceAFichaDeDispositivo(enlace.attr("href")))
        .map(enlace -> enlace.absUrl("href"))
        .filter(urlAbsoluta -> !urlAbsoluta.isBlank())
        .findFirst();
  }

  private boolean esEnlaceAFichaDeDispositivo(String href) {
    return ENLACE_FICHA_DISPOSITIVO.matcher(href).matches() && !href.contains("-news-") && !href.contains("-review-");
  }

  private String extraerFicha(Document paginaFicha) {
    StringBuilder ficha = new StringBuilder();
    String nombreDispositivo = paginaFicha.select("h1.specs-phone-name-title").text();
    if (!nombreDispositivo.isBlank()) {
      ficha.append("Dispositivo: ").append(nombreDispositivo).append('\n');
    }
    for (Element tablaSeccion : paginaFicha.select("#specs-list table")) {
      String seccion = tablaSeccion.select("th").text();
      if (seccion.equalsIgnoreCase("Network")) {
        continue;
      }
      for (Element fila : tablaSeccion.select("tr")) {
        String valor = fila.select("td.nfo").text();
        if (valor.isBlank()) {
          continue;
        }
        ficha.append(seccion).append(" - ").append(fila.select("td.ttl").text()).append(": ").append(valor).append('\n');
      }
    }
    return ficha.length() > LONGITUD_MAXIMA_FICHA ? ficha.substring(0, LONGITUD_MAXIMA_FICHA) : ficha.toString();
  }
}
