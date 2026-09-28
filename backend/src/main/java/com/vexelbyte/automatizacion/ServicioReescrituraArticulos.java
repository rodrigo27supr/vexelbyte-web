package com.vexelbyte.automatizacion;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.EspecificacionTecnica;
import com.vexelbyte.automatizacion.busqueda.ClienteBusquedaTavily;
import com.vexelbyte.automatizacion.busqueda.FragmentoAnalisis;
import com.vexelbyte.automatizacion.gsmarena.ClienteNoticiaGsmarena;
import com.vexelbyte.automatizacion.gsmarena.MaterialNoticiaGsmarena;
import com.vexelbyte.automatizacion.ia.PipelineGeneracionContenido;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido.ContenidoAprobado;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import com.vexelbyte.automatizacion.ia.SolicitudGeneracionContenido;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Reescribe con la cobertura de otros medios los articulos redactados solo con
// su fuente (decision del Tech Lead, 2026-09-27). Conserva la URL, la foto, la
// seccion y las fechas: solo cambia el texto y marca la fecha de actualizacion.
@Component
public class ServicioReescrituraArticulos {

  private static final Logger REGISTRO = LoggerFactory.getLogger(ServicioReescrituraArticulos.class);

  // Cubre el archivo entero (unos 55 articulos en produccion) en el primer
  // ciclo tras el despliegue, para que la web salga ya reescrita (decision del
  // Tech Lead, 2026-09-27). Si se agota la cuota o Render se duerme a mitad, lo
  // pendiente sigue en el siguiente ciclo: cada articulo se guarda por separado.
  private static final int MAXIMO_REESCRITURAS_POR_CICLO = 80;

  private static final int LONGITUD_MAXIMA_DESCRIPCION = 500;
  private static final int MINIMO_PALABRAS_TITULAR_RECONSTRUIDO = 3;

  private static final Pattern PARRAFO_CREDITO_FUENTES = Pattern.compile("<p>(Fuente original|Fuentes): .*</p>\\s*$");
  private static final Pattern ETIQUETA_HTML = Pattern.compile("<[^>]+>");
  private static final Pattern ESPACIOS_REPETIDOS = Pattern.compile("\\s+");
  // GSMArena termina sus URL en "-news-74753.php"; TechPowerUp, en el titular sin extension.
  private static final Pattern SUFIJO_RUTA_GSMARENA = Pattern.compile("-news-\\d+$");
  private static final Pattern EXTENSION_RUTA = Pattern.compile("\\.[a-z]+$");
  private static final Pattern SEPARADORES_RUTA = Pattern.compile("[-_]+");

  private static final TypeReference<List<EspecificacionTecnica>> TIPO_LISTA_ESPECIFICACIONES =
      new TypeReference<>() {
      };

  private final ArticuloRepository repositorioArticulos;
  private final ClienteBusquedaTavily clienteBusqueda;
  private final ClienteNoticiaGsmarena clienteNoticiaGsmarena;
  private final PipelineGeneracionContenido pipelineGeneracionContenido;
  private final ObjectMapper mapeadorJson;
  private final Clock reloj;

  public ServicioReescrituraArticulos(
      ArticuloRepository repositorioArticulos,
      ClienteBusquedaTavily clienteBusqueda,
      ClienteNoticiaGsmarena clienteNoticiaGsmarena,
      PipelineGeneracionContenido pipelineGeneracionContenido,
      ObjectMapper mapeadorJson,
      Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.clienteBusqueda = clienteBusqueda;
    this.clienteNoticiaGsmarena = clienteNoticiaGsmarena;
    this.pipelineGeneracionContenido = pipelineGeneracionContenido;
    this.mapeadorJson = mapeadorJson;
    this.reloj = reloj;
  }

  public void reescribirArticulosPendientes() {
    // Sin busqueda no hay nada que anadir al articulo: reescribirlo con el mismo
    // material solo gastaria cuota en parafrasearlo.
    if (!clienteBusqueda.estaConfigurado()) {
      return;
    }
    List<Articulo> pendientes = repositorioArticulos
        .findByBorradorFalseAndRedaccionRevisadaFalseOrderByFechaPublicacionDesc(Limit.of(MAXIMO_REESCRITURAS_POR_CICLO));
    if (pendientes.isEmpty()) {
      return;
    }
    int articulosReescritos = 0;
    for (Articulo articulo : pendientes) {
      try {
        if (reescribirArticulo(articulo)) {
          articulosReescritos++;
        }
      } catch (SinProveedorIADisponibleException sinProveedor) {
        REGISTRO.warn("Ningun proveedor de IA disponible; dejo la reescritura para el siguiente ciclo");
        break;
      } catch (RuntimeException falloReescritura) {
        // Fallo de red de la busqueda, la fuente o la IA: el articulo sigue
        // pendiente y se reintenta en el siguiente ciclo.
        REGISTRO.warn("No pude reescribir el articulo {}: {}", articulo.getId(), falloReescritura.getMessage());
      }
    }
    REGISTRO.info("Reescritura: {} de {} articulos reescritos con cobertura de otros medios",
        articulosReescritos, pendientes.size());
  }

  private boolean reescribirArticulo(Articulo articulo) {
    String titularFuente = reconstruirTitularDeLaFuente(articulo);
    String medioDeOrigen = CreditoFuentesArticulo.extraerDominioSiEsEnlaceWeb(articulo.getEnlaceFuente()).orElse(null);
    List<FragmentoAnalisis> cobertura = clienteBusqueda.buscarCoberturaDelUltimoMes(titularFuente, medioDeOrigen);
    if (cobertura.isEmpty()) {
      REGISTRO.info("Sin cobertura de otros medios para el articulo {}; conservo su texto", articulo.getId());
      marcarRedaccionRevisada(articulo);
      return false;
    }

    ResultadoGeneracionContenido resultado = pipelineGeneracionContenido.generarArticulo(
        construirSolicitud(articulo, titularFuente, cobertura));
    if (!(resultado instanceof ContenidoAprobado aprobado)) {
      // El articulo publicado ya paso todas las validaciones: si la nueva
      // redaccion no las pasa, me quedo con la que habia y no la reintento.
      REGISTRO.warn("La reescritura del articulo {} no paso la validacion ({}); conservo su texto",
          articulo.getId(), resultado);
      marcarRedaccionRevisada(articulo);
      return false;
    }
    aplicarNuevaRedaccion(articulo, aprobado, cobertura);
    REGISTRO.info("Articulo {} reescrito con {} medios de cobertura", articulo.getId(), cobertura.size());
    return true;
  }

  private SolicitudGeneracionContenido construirSolicitud(
      Articulo articulo, String titularFuente, List<FragmentoAnalisis> cobertura) {
    String textoFuente = extraerTextoDelArticulo(articulo.getCuerpoHtml());
    String fichaTecnica = formatearFichaGuardada(articulo.getEspecificacionesJson());
    // GSMArena permite volver a leer la noticia completa, mas larga que nuestro
    // texto original; de TechPowerUp solo tenia el extracto del RSS.
    if (clienteNoticiaGsmarena.esNoticiaDeGsmarena(articulo.getEnlaceFuente())) {
      try {
        MaterialNoticiaGsmarena material = clienteNoticiaGsmarena.obtenerMaterial(articulo.getEnlaceFuente());
        if (material.textoCuerpo().length() > textoFuente.length()) {
          textoFuente = material.textoCuerpo();
        }
        if (material.fichaTecnica() != null) {
          fichaTecnica = material.fichaTecnica();
        }
      } catch (RuntimeException falloPagina) {
        REGISTRO.warn("No pude releer la fuente del articulo {}: {}", articulo.getId(), falloPagina.getMessage());
      }
    }
    return new SolicitudGeneracionContenido(
        titularFuente, textoFuente, fichaTecnica, false, PreparadorMaterialFuente.formatearCobertura(cobertura));
  }

  // No guardo el titular en ingles de la fuente, pero su URL lo lleva
  // ("/353113/pny-reportedly-denies-melted-rtx-5090-warranty-claim"): es la
  // mejor consulta para encontrar la misma noticia en otros medios.
  private String reconstruirTitularDeLaFuente(Articulo articulo) {
    if (articulo.getEnlaceFuente() == null) {
      return articulo.getTitulo();
    }
    try {
      String ruta = URI.create(articulo.getEnlaceFuente()).getPath();
      String ultimoTramo = ruta == null ? "" : ruta.substring(ruta.lastIndexOf('/') + 1);
      String sinSufijos = SUFIJO_RUTA_GSMARENA.matcher(EXTENSION_RUTA.matcher(ultimoTramo).replaceFirst("")).replaceFirst("");
      String titular = SEPARADORES_RUTA.matcher(sinSufijos).replaceAll(" ").strip();
      if (titular.split(" ").length >= MINIMO_PALABRAS_TITULAR_RECONSTRUIDO) {
        return titular;
      }
    } catch (IllegalArgumentException enlaceMalformado) {
      REGISTRO.debug("El enlace de la fuente del articulo {} no sirve de titular", articulo.getId());
    }
    return articulo.getTitulo();
  }

  // El texto publicado ya es fiel a la fuente (paso la compuerta de material y
  // la validacion): sirve de material sin el parrafo de creditos.
  private String extraerTextoDelArticulo(String cuerpoHtml) {
    String cuerpoSinCreditos = PARRAFO_CREDITO_FUENTES.matcher(cuerpoHtml).replaceFirst("");
    String textoPlano = HtmlUtils.htmlUnescape(ETIQUETA_HTML.matcher(cuerpoSinCreditos).replaceAll(" "));
    return ESPACIOS_REPETIDOS.matcher(textoPlano).replaceAll(" ").strip();
  }

  private String formatearFichaGuardada(String especificacionesJson) {
    if (especificacionesJson == null) {
      return null;
    }
    List<EspecificacionTecnica> especificaciones = mapeadorJson.readValue(especificacionesJson, TIPO_LISTA_ESPECIFICACIONES);
    if (especificaciones.isEmpty()) {
      return null;
    }
    return especificaciones.stream()
        .map(especificacion -> especificacion.nombre() + ": " + especificacion.valor())
        .collect(Collectors.joining("\n"));
  }

  private void aplicarNuevaRedaccion(Articulo articulo, ContenidoAprobado aprobado, List<FragmentoAnalisis> cobertura) {
    List<String> enlacesFuentes = new ArrayList<>();
    enlacesFuentes.add(articulo.getEnlaceFuente());
    cobertura.stream().map(FragmentoAnalisis::url).forEach(enlacesFuentes::add);

    articulo.setTitulo(aprobado.titulo());
    articulo.setDescripcion(recortarDescripcion(aprobado.entradilla()));
    articulo.setCuerpoHtml(CreditoFuentesArticulo.anadirCreditoFuentes(aprobado.cuerpoHtml(), enlacesFuentes));
    // La seccion no cambia: moverla alteraria el ritmo de publicacion por seccion.
    if (aprobado.producto() != null) {
      articulo.setProducto(aprobado.producto());
    }
    // Una redaccion sin ficha no borra la que ya tenia el articulo.
    if (!aprobado.especificaciones().isEmpty()) {
      articulo.setEspecificacionesJson(mapeadorJson.writeValueAsString(aprobado.especificaciones()));
    }
    if (!aprobado.puntosClave().isEmpty()) {
      articulo.setPuntosClaveJson(mapeadorJson.writeValueAsString(aprobado.puntosClave()));
    }
    articulo.setFechaActualizacion(OffsetDateTime.now(reloj));
    articulo.setRedaccionRevisada(true);
    repositorioArticulos.save(articulo);
  }

  private void marcarRedaccionRevisada(Articulo articulo) {
    articulo.setRedaccionRevisada(true);
    repositorioArticulos.save(articulo);
  }

  private String recortarDescripcion(String descripcion) {
    return descripcion.length() > LONGITUD_MAXIMA_DESCRIPCION
        ? descripcion.substring(0, LONGITUD_MAXIMA_DESCRIPCION)
        : descripcion;
  }
}
