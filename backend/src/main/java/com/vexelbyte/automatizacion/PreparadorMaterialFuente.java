package com.vexelbyte.automatizacion;

import com.vexelbyte.automatizacion.MaterialFuente.Aplazado;
import com.vexelbyte.automatizacion.MaterialFuente.MaterialListo;
import com.vexelbyte.automatizacion.MaterialFuente.SinMaterial;
import com.vexelbyte.automatizacion.busqueda.ClienteBusquedaTavily;
import com.vexelbyte.automatizacion.busqueda.FragmentoAnalisis;
import com.vexelbyte.automatizacion.gsmarena.ClienteNoticiaGsmarena;
import com.vexelbyte.automatizacion.gsmarena.MaterialNoticiaGsmarena;
import com.vexelbyte.automatizacion.ia.SolicitudGeneracionContenido;
import com.vexelbyte.automatizacion.rss.NoticiaHardwareRss;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Reune el material que recibe la IA. Con solo el titular escribe relleno que
// no cumple lo que promete (paso con el analisis del iPhone 18 Pro, cuyo RSS
// venia vacio): sin material suficiente la noticia no llega a redactarse.
@Component
public class PreparadorMaterialFuente {

  private static final Logger REGISTRO = LoggerFactory.getLogger(PreparadorMaterialFuente.class);

  // Por debajo de esto no hay datos para un articulo de varias secciones. Los
  // extractos normales rondan 600 caracteres (GSMArena) y 800-2.000 (TechPowerUp).
  private static final int LONGITUD_MINIMA_TEXTO_FUENTE = 400;

  // Con menos medios no hay nada que contrastar: seria el resumen de uno solo.
  private static final int MINIMO_MEDIOS_CON_ANALISIS = 3;

  private static final String MEDIO_GSMARENA = "gsmarena.com";

  private static final Pattern SUFIJO_ANALISIS_EN_TITULAR =
      Pattern.compile("(?i)\\s+(long[- ]term\\s+|hands[- ]on\\s+|camera\\s+)?review\\b.*$");

  private static final String MOTIVO_FUENTE_SIN_TEXTO =
      "La fuente no trae texto suficiente para redactar sin inventar";
  private static final String MOTIVO_SIN_BUSQUEDA =
      "Analisis de GSMArena sin busqueda configurada: no hay material de otros medios que resumir";
  private static final String PLANTILLA_MOTIVO_POCOS_MEDIOS =
      "Solo %d medios han publicado analisis de %s: no hay suficiente para contrastar";

  private final ClienteNoticiaGsmarena clienteNoticiaGsmarena;
  private final ClienteBusquedaTavily clienteBusqueda;

  public PreparadorMaterialFuente(ClienteNoticiaGsmarena clienteNoticiaGsmarena, ClienteBusquedaTavily clienteBusqueda) {
    this.clienteNoticiaGsmarena = clienteNoticiaGsmarena;
    this.clienteBusqueda = clienteBusqueda;
  }

  public boolean esAnalisisDeTerceros(NoticiaHardwareRss noticia) {
    return clienteNoticiaGsmarena.esAnalisisDeGsmarena(noticia.enlaceOriginal());
  }

  public MaterialFuente preparar(NoticiaHardwareRss noticia) {
    return esAnalisisDeTerceros(noticia) ? prepararResumenDeAnalisis(noticia) : prepararNoticia(noticia);
  }

  private MaterialFuente prepararNoticia(NoticiaHardwareRss noticia) {
    String textoFuente = noticia.resumen();
    String fichaTecnica = null;
    if (clienteNoticiaGsmarena.esNoticiaDeGsmarena(noticia.enlaceOriginal())) {
      try {
        MaterialNoticiaGsmarena material = clienteNoticiaGsmarena.obtenerMaterial(noticia.enlaceOriginal());
        if (material.textoCuerpo().length() > textoFuente.length()) {
          textoFuente = material.textoCuerpo();
        }
        fichaTecnica = material.fichaTecnica();
      } catch (RuntimeException excepcionPagina) {
        // La pagina enriquece pero no es imprescindible: sigo con el extracto
        // del RSS. Si ese extracto no llega al minimo, lo reintento el proximo
        // ciclo en vez de descartarlo por un fallo de red.
        REGISTRO.warn("No pude leer la pagina de '{}': {}", noticia.titulo(), excepcionPagina.getMessage());
        if (textoFuente.length() < LONGITUD_MINIMA_TEXTO_FUENTE) {
          return new Aplazado();
        }
      }
    }
    if (textoFuente.length() < LONGITUD_MINIMA_TEXTO_FUENTE) {
      return new SinMaterial(MOTIVO_FUENTE_SIN_TEXTO);
    }
    List<FragmentoAnalisis> cobertura = buscarCoberturaSiEsPosible(noticia);
    List<String> enlacesFuentes = new ArrayList<>();
    enlacesFuentes.add(noticia.enlaceOriginal());
    cobertura.stream().map(FragmentoAnalisis::url).forEach(enlacesFuentes::add);
    return new MaterialListo(
        new SolicitudGeneracionContenido(
            noticia.titulo(), textoFuente, fichaTecnica, false,
            cobertura.isEmpty() ? null : formatearCobertura(cobertura)),
        enlacesFuentes);
  }

  // Con una sola fuente los articulos salian cortos (decision del Tech Lead,
  // 2026-09-27): otros medios aportan datos y contexto. Es un complemento: si
  // la busqueda falla o no hay clave, el articulo se redacta con la fuente.
  private List<FragmentoAnalisis> buscarCoberturaSiEsPosible(NoticiaHardwareRss noticia) {
    if (!clienteBusqueda.estaConfigurado()) {
      return List.of();
    }
    try {
      return clienteBusqueda.buscarCobertura(noticia.titulo(), extraerMedio(noticia.enlaceOriginal()));
    } catch (RuntimeException falloBusqueda) {
      REGISTRO.warn("No pude buscar cobertura de '{}': {}", noticia.titulo(), falloBusqueda.getMessage());
      return List.of();
    }
  }

  // Tambien lo usa la reescritura de articulos ya publicados: el mismo formato
  // que ya sabe leer el prompt de redaccion.
  static String formatearCobertura(List<FragmentoAnalisis> cobertura) {
    return cobertura.stream()
        .map(fragmento -> "Medio: %s%nTitular: %s%nFragmento: %s".formatted(
            fragmento.medio(), fragmento.titulo(), fragmento.texto()))
        .collect(Collectors.joining("\n\n"));
  }

  private String extraerMedio(String enlace) {
    try {
      String host = URI.create(enlace).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    } catch (IllegalArgumentException enlaceMalformado) {
      return null;
    }
  }

  // Los analisis de GSMArena reparten las pruebas en varias paginas y el RSS no
  // trae ni una linea. En vez de fingir unas pruebas que no hicimos, resumo lo
  // que han publicado varios medios, atribuyendo cada valoracion.
  private MaterialFuente prepararResumenDeAnalisis(NoticiaHardwareRss noticia) {
    if (!clienteBusqueda.estaConfigurado()) {
      return new SinMaterial(MOTIVO_SIN_BUSQUEDA);
    }
    String producto = extraerProductoDelTitular(noticia.titulo());
    List<FragmentoAnalisis> fragmentos;
    try {
      fragmentos = clienteBusqueda.buscarAnalisis(producto);
    } catch (RuntimeException falloBusqueda) {
      REGISTRO.warn("Fallo la busqueda de analisis de '{}': {}", producto, falloBusqueda.getMessage());
      return new Aplazado();
    }
    // Descarte definitivo y no aplazamiento: repetir la busqueda en cada ciclo
    // gastaria creditos de Tavily sin garantia de encontrar mas medios.
    if (fragmentos.size() < MINIMO_MEDIOS_CON_ANALISIS) {
      return new SinMaterial(PLANTILLA_MOTIVO_POCOS_MEDIOS.formatted(fragmentos.size(), producto));
    }
    return new MaterialListo(
        new SolicitudGeneracionContenido(
            producto + " review", formatearFragmentos(producto, fragmentos), obtenerFichaSiExiste(noticia), true,
            null),
        reunirEnlacesFuentes(noticia, fragmentos));
  }

  private String extraerProductoDelTitular(String titular) {
    return SUFIJO_ANALISIS_EN_TITULAR.matcher(titular).replaceFirst("").strip();
  }

  private String formatearFragmentos(String producto, List<FragmentoAnalisis> fragmentos) {
    String bloquesPorMedio = fragmentos.stream()
        .map(fragmento -> "Medio: %s%nTitular: %s%nFragmento: %s".formatted(
            fragmento.medio(), fragmento.titulo(), fragmento.texto()))
        .collect(Collectors.joining("\n\n"));
    return "Análisis publicados por otros medios sobre " + producto + ":\n\n" + bloquesPorMedio;
  }

  private String obtenerFichaSiExiste(NoticiaHardwareRss noticia) {
    try {
      return clienteNoticiaGsmarena.obtenerMaterial(noticia.enlaceOriginal()).fichaTecnica();
    } catch (RuntimeException excepcionPagina) {
      REGISTRO.warn("No pude leer la ficha del analisis '{}': {}", noticia.titulo(), excepcionPagina.getMessage());
      return null;
    }
  }

  // El analisis de GSMArena va primero porque es el que origina la pieza; si
  // la busqueda tambien lo devolvio, no lo repito.
  private List<String> reunirEnlacesFuentes(NoticiaHardwareRss noticia, List<FragmentoAnalisis> fragmentos) {
    List<String> enlaces = new ArrayList<>();
    enlaces.add(noticia.enlaceOriginal());
    fragmentos.stream()
        .filter(fragmento -> !fragmento.medio().equals(MEDIO_GSMARENA))
        .map(FragmentoAnalisis::url)
        .forEach(enlaces::add);
    return enlaces;
  }
}
