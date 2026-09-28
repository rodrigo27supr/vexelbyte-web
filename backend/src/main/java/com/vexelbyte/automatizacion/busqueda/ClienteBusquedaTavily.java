package com.vexelbyte.automatizacion.busqueda;

import com.vexelbyte.automatizacion.busqueda.RespuestaBusquedaTavily.ResultadoTavily;
import com.vexelbyte.automatizacion.config.PropiedadesBusquedaTavily;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Busca lo que otros medios han publicado: de un producto, para resumir sus
// analisis, y de cada noticia, para ampliar el articulo. Uso Tavily y no la
// busqueda de Google en Gemini porque los terminos de Google prohiben
// publicar lo que genera con ella.
@Component
public class ClienteBusquedaTavily {

  // Una busqueda avanzada cuesta 2 creditos de los 1.000 gratuitos al mes.
  private static final String PROFUNDIDAD_BUSQUEDA = "advanced";
  private static final int FRAGMENTOS_POR_FUENTE = 3;
  private static final int MAXIMO_RESULTADOS = 10;
  private static final String PERIODO_BUSQUEDA = "month";
  private static final String PERIODO_COBERTURA = "week";
  private static final String TEMA_GENERAL = "general";
  private static final String TEMA_NOTICIAS = "news";
  private static final int MAXIMO_MEDIOS_COBERTURA = 5;
  private static final int MINIMO_PALABRAS_COMPARTIDAS = 2;

  private static final Pattern SEPARADOR_PALABRAS = Pattern.compile("[^a-z0-9]+");
  private static final Pattern PREFIJO_FORO = Pattern.compile("(forums?|community|comunidad)\\.");
  // Los titulares de los feeds vienen en ingles: estas palabras no distinguen una noticia de otra.
  private static final Set<String> PALABRAS_VACIAS = Set.of(
      "the", "and", "for", "with", "from", "that", "this", "its", "new", "now", "are", "has", "have", "will",
      "into", "over", "after", "about", "more", "than", "gets", "get", "all", "you", "your", "our", "out", "not",
      "but", "can", "via", "how", "why", "what", "here", "just", "set", "may", "could", "says", "reportedly");

  // Un fragmento por medio y como mucho seis medios: suficiente para
  // contrastar opiniones sin disparar el tamano del prompt.
  private static final int MAXIMO_MEDIOS = 6;
  private static final int LONGITUD_MINIMA_FRAGMENTO = 150;
  private static final int LONGITUD_MAXIMA_FRAGMENTO = 1500;

  // Redes, foros y tiendas no son analisis de un medio.
  private static final List<String> DOMINIOS_EXCLUIDOS = List.of(
      "youtube.com", "reddit.com", "x.com", "twitter.com", "facebook.com", "instagram.com", "tiktok.com",
      "pinterest.com", "quora.com", "amazon.com", "ebay.com", "aliexpress.com");

  // La cobertura se cita en el articulo: solo medios tecnologicos reconocidos.
  // Sin esta lista se colaban granjas de contenido que reescriben a otros
  // (moshirewritten.com, megamobilecontent.com) y restaban credibilidad.
  private static final List<String> MEDIOS_RECONOCIDOS_COBERTURA = List.of(
      "tomshardware.com", "techspot.com", "videocardz.com", "wccftech.com", "techpowerup.com", "guru3d.com",
      "kitguru.net", "overclock3d.net", "hothardware.com", "tweaktown.com", "pcgamer.com", "pcworld.com",
      "pcmag.com", "arstechnica.com", "theverge.com", "engadget.com", "techradar.com", "tomsguide.com", "cnet.com",
      "zdnet.com", "digitaltrends.com", "notebookcheck.net", "gsmarena.com", "phonearena.com",
      "androidauthority.com", "androidcentral.com", "androidpolice.com", "xda-developers.com", "9to5google.com",
      "9to5mac.com", "macrumors.com", "appleinsider.com", "sammobile.com", "gizmochina.com", "fonearena.com",
      "beebom.com", "gadgets360.com", "dpreview.com", "windowscentral.com", "neowin.net", "theregister.com",
      "techcrunch.com", "bleepingcomputer.com", "igorslab.de", "computerbase.de", "hardwareluxx.de", "xataka.com",
      "hardzone.es", "elchapuzasinformatico.com", "profesionalreview.com", "muycomputer.com");

  private final RestClient clienteHttp;
  private final PropiedadesBusquedaTavily propiedadesTavily;

  public ClienteBusquedaTavily(
      @Qualifier("clienteHttpTavily") RestClient clienteHttp, PropiedadesBusquedaTavily propiedadesTavily) {
    this.clienteHttp = clienteHttp;
    this.propiedadesTavily = propiedadesTavily;
  }

  public boolean estaConfigurado() {
    return propiedadesTavily.tieneClave();
  }

  public List<FragmentoAnalisis> buscarAnalisis(String producto) {
    List<ResultadoTavily> resultados = buscar(new SolicitudBusquedaTavily(
        "\"" + producto + "\" review", TEMA_GENERAL, PROFUNDIDAD_BUSQUEDA, FRAGMENTOS_POR_FUENTE, MAXIMO_RESULTADOS,
        PERIODO_BUSQUEDA, DOMINIOS_EXCLUIDOS, List.of()));
    return seleccionarFragmentos(resultados, resultado -> hablaDelProducto(resultado, producto), null, MAXIMO_MEDIOS);
  }

  // Lo que otros medios han publicado de la misma noticia en la ultima semana,
  // para que el articulo no dependa de una sola fuente (GSMArena da unos
  // 1.500 caracteres). El medio de origen se excluye: su texto ya lo tengo.
  public List<FragmentoAnalisis> buscarCobertura(String titular, String medioDeOrigen) {
    return buscarCoberturaEnPeriodo(titular, medioDeOrigen, PERIODO_COBERTURA);
  }

  // Para reescribir articulos ya publicados: la noticia puede tener mas de una
  // semana y su cobertura quedaria fuera de la ventana normal.
  public List<FragmentoAnalisis> buscarCoberturaDelUltimoMes(String titular, String medioDeOrigen) {
    return buscarCoberturaEnPeriodo(titular, medioDeOrigen, PERIODO_BUSQUEDA);
  }

  private List<FragmentoAnalisis> buscarCoberturaEnPeriodo(String titular, String medioDeOrigen, String periodo) {
    List<ResultadoTavily> resultados = buscar(new SolicitudBusquedaTavily(
        titular, TEMA_NOTICIAS, PROFUNDIDAD_BUSQUEDA, FRAGMENTOS_POR_FUENTE, MAXIMO_RESULTADOS,
        periodo, DOMINIOS_EXCLUIDOS, MEDIOS_RECONOCIDOS_COBERTURA));
    Set<String> palabrasTitular = extraerPalabrasSignificativas(titular);
    // Compruebo tambien el dominio aqui: la lista de Tavily es un filtro de su
    // lado, y lo que se cita en el articulo no puede depender solo de el.
    return seleccionarFragmentos(
        resultados,
        resultado -> esDeMedioReconocido(resultado) && compartePalabrasConElTitular(resultado, palabrasTitular),
        medioDeOrigen, MAXIMO_MEDIOS_COBERTURA);
  }

  private List<ResultadoTavily> buscar(SolicitudBusquedaTavily solicitud) {
    RespuestaBusquedaTavily respuesta = clienteHttp.post()
        .uri("/search")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + propiedadesTavily.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(solicitud)
        .retrieve()
        .body(RespuestaBusquedaTavily.class);
    return respuesta == null || respuesta.results() == null ? List.of() : respuesta.results();
  }

  private List<FragmentoAnalisis> seleccionarFragmentos(
      List<ResultadoTavily> resultados, Predicate<ResultadoTavily> esRelevante, String medioExcluido, int maximoMedios) {
    Set<String> mediosIncluidos = new HashSet<>();
    if (medioExcluido != null) {
      mediosIncluidos.add(medioExcluido);
    }
    return resultados.stream()
        .filter(Objects::nonNull)
        .filter(resultado -> resultado.content() != null && resultado.content().strip().length() >= LONGITUD_MINIMA_FRAGMENTO)
        .filter(esRelevante)
        .map(this::convertirEnFragmento)
        .flatMap(Optional::stream)
        .filter(fragmento -> mediosIncluidos.add(fragmento.medio()))
        .limit(maximoMedios)
        .toList();
  }

  // La busqueda por titular trae a veces noticias vecinas del mismo tema: exijo
  // que el resultado repita al menos dos palabras significativas del titular.
  private boolean compartePalabrasConElTitular(ResultadoTavily resultado, Set<String> palabrasTitular) {
    Set<String> palabrasResultado = extraerPalabrasSignificativas(
        (resultado.title() == null ? "" : resultado.title()) + " " + resultado.content());
    return palabrasTitular.stream().filter(palabrasResultado::contains).count() >= MINIMO_PALABRAS_COMPARTIDAS;
  }

  // Los foros de un medio reconocido (forums.guru3d.com) son mensajes de
  // usuarios, no su redaccion: en la reescritura del archivo se colaron cuatro.
  private boolean esDeMedioReconocido(ResultadoTavily resultado) {
    return extraerMedio(resultado.url())
        .filter(medio -> !PREFIJO_FORO.matcher(medio).lookingAt())
        .map(medio -> MEDIOS_RECONOCIDOS_COBERTURA.stream()
            .anyMatch(medioReconocido -> medio.equals(medioReconocido) || medio.endsWith("." + medioReconocido)))
        .orElse(false);
  }

  private Set<String> extraerPalabrasSignificativas(String texto) {
    return Arrays.stream(SEPARADOR_PALABRAS.split(texto.toLowerCase(Locale.ROOT)))
        .filter(palabra -> palabra.length() >= 3 && !PALABRAS_VACIAS.contains(palabra))
        .collect(Collectors.toSet());
  }

  // La busqueda devuelve a veces comparativas o el modelo hermano: exijo que
  // el fragmento nombre el producto, con o sin la marca delante.
  private boolean hablaDelProducto(ResultadoTavily resultado, String producto) {
    String textoResultado = ((resultado.title() == null ? "" : resultado.title()) + " " + resultado.content())
        .toLowerCase(Locale.ROOT);
    String nombreProducto = producto.toLowerCase(Locale.ROOT).strip();
    String nombreSinMarca = nombreProducto.contains(" ")
        ? nombreProducto.substring(nombreProducto.indexOf(' ') + 1)
        : nombreProducto;
    return textoResultado.contains(nombreProducto) || textoResultado.contains(nombreSinMarca);
  }

  private Optional<FragmentoAnalisis> convertirEnFragmento(ResultadoTavily resultado) {
    return extraerMedio(resultado.url()).map(medio -> new FragmentoAnalisis(
        medio,
        resultado.title() == null ? "" : resultado.title().strip(),
        resultado.url(),
        recortar(resultado.content().strip())));
  }

  // Solo acepto enlaces web: la URL acaba como enlace en el credito del articulo.
  private Optional<String> extraerMedio(String url) {
    if (url == null) {
      return Optional.empty();
    }
    try {
      URI uriResultado = URI.create(url);
      String esquema = uriResultado.getScheme();
      boolean esEnlaceWeb = "https".equalsIgnoreCase(esquema) || "http".equalsIgnoreCase(esquema);
      if (!esEnlaceWeb || uriResultado.getHost() == null) {
        return Optional.empty();
      }
      return Optional.of(uriResultado.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""));
    } catch (IllegalArgumentException urlInvalida) {
      return Optional.empty();
    }
  }

  private String recortar(String texto) {
    return texto.length() > LONGITUD_MAXIMA_FRAGMENTO ? texto.substring(0, LONGITUD_MAXIMA_FRAGMENTO) : texto;
  }
}
