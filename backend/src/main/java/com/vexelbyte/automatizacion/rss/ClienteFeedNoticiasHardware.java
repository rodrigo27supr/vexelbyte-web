package com.vexelbyte.automatizacion.rss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

// Lee feeds RSS 2.0 publicos de medios de hardware y moviles (las URLs viven
// en PropiedadesFeedHardware). Sustituye a Steam GetNewsForApp, cuyas notas de
// parche de juegos no encajaban con la linea editorial de VexelByte.
@Component
public class ClienteFeedNoticiasHardware {

  // Tope del texto que llega a la IA. TechPowerUp mete la noticia casi entera
  // en description (800-2.200 caracteres): la dejo pasar completa, con margen,
  // porque cuanto mas dato real recibe la IA menos relleno escribe. GSMArena
  // solo trae un extracto, que se sustituye por la pagina completa.
  private static final int LONGITUD_MAXIMA_RESUMEN = 4000;

  private static final Pattern ETIQUETA_HTML = Pattern.compile("<[^>]*>");
  private static final Pattern ESPACIOS_REPETIDOS = Pattern.compile("\\s+");

  private final RestClient clienteHttp;
  private final RetryTemplate plantillaReintentos;

  public ClienteFeedNoticiasHardware(
      @Qualifier("clienteHttpFeedHardware") RestClient clienteHttp,
      RetryTemplate plantillaReintentosApisExternas) {
    this.clienteHttp = clienteHttp;
    this.plantillaReintentos = plantillaReintentosApisExternas;
  }

  public List<NoticiaHardwareRss> obtenerNoticiasRecientes(String urlFeed) {
    String xmlCrudo = plantillaReintentos.execute(contextoReintento ->
        clienteHttp.get().uri(urlFeed).retrieve().body(String.class));
    return extraerNoticias(xmlCrudo);
  }

  private List<NoticiaHardwareRss> extraerNoticias(String xmlCrudo) {
    Document documento = parsearXml(xmlCrudo);
    NodeList elementosItem = documento.getElementsByTagName("item");
    List<NoticiaHardwareRss> noticias = new ArrayList<>();

    for (int posicionItem = 0; posicionItem < elementosItem.getLength(); posicionItem++) {
      Element elementoItem = (Element) elementosItem.item(posicionItem);
      String identificador = textoDeHijoDirecto(elementoItem, "guid");
      String titulo = textoDeHijoDirecto(elementoItem, "title");
      // Sin guid no puedo deduplicar y sin titulo no hay noticia: descarto el
      // item en vez de ingerir algo que no podria identificar la proxima vez.
      if (identificador.isBlank() || titulo.isBlank()) {
        continue;
      }
      noticias.add(new NoticiaHardwareRss(
          identificador,
          titulo,
          textoDeHijoDirecto(elementoItem, "link"),
          convertirATextoPlano(textoDeHijoDirecto(elementoItem, "description")),
          parsearFecha(textoDeHijoDirecto(elementoItem, "pubDate"))));
    }
    return noticias;
  }

  private Document parsearXml(String xmlCrudo) {
    try {
      DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
      // El feed es una entrada externa no confiable: bloqueo DOCTYPE y
      // entidades externas para cerrar la puerta a XXE (OWASP A05).
      fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(xmlCrudo.getBytes(StandardCharsets.UTF_8)));
    } catch (ParserConfigurationException | SAXException | IOException excepcionParseo) {
      throw new IllegalStateException("El feed de noticias no es un XML valido: " + excepcionParseo.getMessage(),
          excepcionParseo);
    }
  }

  // Solo miro hijos directos: dentro de cada item conviven dc:description,
  // media:description y demas etiquetas con nombre parecido.
  private String textoDeHijoDirecto(Element padre, String nombreEtiqueta) {
    for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
      if (hijo instanceof Element elementoHijo && nombreEtiqueta.equals(elementoHijo.getTagName())) {
        return elementoHijo.getTextContent().strip();
      }
    }
    return "";
  }

  // GSMArena mete una <img> y parrafos escapados dentro de description: al
  // modelo le paso solo el texto, sin marcado ni URLs de imagenes.
  private String convertirATextoPlano(String descripcionConHtml) {
    String textoPlano = ESPACIOS_REPETIDOS
        .matcher(ETIQUETA_HTML.matcher(descripcionConHtml).replaceAll(" "))
        .replaceAll(" ")
        .strip();
    return textoPlano.length() > LONGITUD_MAXIMA_RESUMEN
        ? textoPlano.substring(0, LONGITUD_MAXIMA_RESUMEN)
        : textoPlano;
  }

  private Instant parsearFecha(String fechaRfc1123) {
    try {
      return ZonedDateTime.parse(fechaRfc1123, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException fechaIlegible) {
      // Una fecha rota no justifica perder la noticia: uso el momento de la
      // ingesta, que es cuando la publico de todas formas.
      return Instant.now();
    }
  }
}
