package com.vexelbyte.automatizacion;

import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.automatizacion.MaterialFuente.Aplazado;
import com.vexelbyte.automatizacion.MaterialFuente.MaterialListo;
import com.vexelbyte.automatizacion.MaterialFuente.SinMaterial;
import com.vexelbyte.automatizacion.config.PropiedadesFeedHardware;
import com.vexelbyte.automatizacion.ia.ClasificadorTitularesIA;
import com.vexelbyte.automatizacion.ia.PipelineGeneracionContenido;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido;
import com.vexelbyte.automatizacion.ia.SinProveedorIADisponibleException;
import com.vexelbyte.automatizacion.imagenes.ServicioFotosArticulos;
import com.vexelbyte.automatizacion.persistencia.DatosEditorialesArticulo;
import com.vexelbyte.automatizacion.persistencia.DatosFuenteArticulo;
import com.vexelbyte.automatizacion.persistencia.RegistroNoticiasDescartadas;
import com.vexelbyte.automatizacion.persistencia.ResultadoIngesta;
import com.vexelbyte.automatizacion.persistencia.ServicioIngestaArticulos;
import com.vexelbyte.automatizacion.rss.ClienteFeedNoticiasHardware;
import com.vexelbyte.automatizacion.rss.NoticiaHardwareRss;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Orquesta el ciclo completo: Extraer (feeds RSS de hardware y moviles) ->
// Preparar material -> Redactar y validar con IA -> Publicar -> Completar
// fotos. Cada noticia aisla sus propios fallos: una noticia problematica no
// debe tumbar el resto del lote, asi que atrapo excepciones por item, nunca
// alrededor de todo el metodo programado.
@Component
public class TrabajoSincronizacionContenido {

  private static final Logger REGISTRO = LoggerFactory.getLogger(TrabajoSincronizacionContenido.class);

  // Lote que revisa el clasificador de titulares en una sola llamada: cubre
  // de sobra lo que publican los feeds entre dos ciclos.
  private static final int MAXIMO_TITULARES_POR_CLASIFICACION = 30;

  private final ClienteFeedNoticiasHardware clienteFeed;
  private final PreparadorMaterialFuente preparadorMaterial;
  private final ClasificadorTitularesIA clasificadorTitulares;
  private final PipelineGeneracionContenido pipelineGeneracionContenido;
  private final ServicioIngestaArticulos servicioIngestaArticulos;
  private final RegistroNoticiasDescartadas registroDescartadas;
  private final ServicioFotosArticulos servicioFotos;
  private final CalendarioSecciones calendarioSecciones;
  private final ServicioReescrituraArticulos servicioReescritura;
  private final PropiedadesFeedHardware propiedadesFeed;

  public TrabajoSincronizacionContenido(
      ClienteFeedNoticiasHardware clienteFeed,
      PreparadorMaterialFuente preparadorMaterial,
      ClasificadorTitularesIA clasificadorTitulares,
      PipelineGeneracionContenido pipelineGeneracionContenido,
      ServicioIngestaArticulos servicioIngestaArticulos,
      RegistroNoticiasDescartadas registroDescartadas,
      ServicioFotosArticulos servicioFotos,
      CalendarioSecciones calendarioSecciones,
      ServicioReescrituraArticulos servicioReescritura,
      PropiedadesFeedHardware propiedadesFeed) {
    this.clienteFeed = clienteFeed;
    this.preparadorMaterial = preparadorMaterial;
    this.clasificadorTitulares = clasificadorTitulares;
    this.pipelineGeneracionContenido = pipelineGeneracionContenido;
    this.servicioIngestaArticulos = servicioIngestaArticulos;
    this.registroDescartadas = registroDescartadas;
    this.servicioFotos = servicioFotos;
    this.calendarioSecciones = calendarioSecciones;
    this.servicioReescritura = servicioReescritura;
    this.propiedadesFeed = propiedadesFeed;
  }

  // Los feeds publican varias noticias al dia, no por minuto: cada 6 horas es
  // suficiente y me mantiene dentro de la cuota de la IA. Empiezo un minuto
  // despues del arranque para no competir con la inicializacion del proceso.
  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
  public void sincronizarNoticiasHardware() {
    redactarNoticiasNuevas();
    // Las noticias nuevas van primero: la reescritura del archivo usa la cuota
    // que sobre, y un fallo suyo no afecta al resto del ciclo.
    reescribirArticulosPendientes();
    // Las fotos no dependen de la IA: se completan aunque el ciclo se corte.
    completarFotosPendientes();
  }

  private void reescribirArticulosPendientes() {
    try {
      servicioReescritura.reescribirArticulosPendientes();
    } catch (RuntimeException falloReescritura) {
      REGISTRO.warn("No pude reescribir los articulos pendientes: {}", falloReescritura.getMessage());
    }
  }

  private void redactarNoticiasNuevas() {
    // Fuera de un dia de publicacion, o si todas las secciones ya publicaron hoy,
    // no hay nada que redactar: ni leo los feeds ni gasto la llamada del clasificador.
    Set<CategoriaArticulo> seccionesAbiertas = EnumSet.noneOf(CategoriaArticulo.class);
    seccionesAbiertas.addAll(calendarioSecciones.obtenerSeccionesAbiertas());
    if (seccionesAbiertas.isEmpty()) {
      REGISTRO.info("Hoy no toca publicar o todas las secciones ya publicaron hoy; no redacto en este ciclo");
      return;
    }

    List<NoticiaHardwareRss> noticias = obtenerNoticiasDeTodosLosFeeds();
    // Proceso de la mas reciente a la mas antigua mezclando feeds: si fuera
    // feed por feed, el tope por ciclo lo agotaria siempre el primero y las
    // noticias de moviles nunca llegarian a la IA.
    noticias.sort(Comparator.comparing(NoticiaHardwareRss::fechaPublicacion).reversed());

    REGISTRO.info("Feeds: {} noticias recibidas en total", noticias.size());
    List<NoticiaHardwareRss> pendientes = noticias.stream()
        .filter(noticia -> !esNoticiaYaResuelta(noticia))
        .limit(MAXIMO_TITULARES_POR_CLASIFICACION)
        .toList();

    List<NoticiaClasificada> candidatas;
    try {
      candidatas = clasificarPorSeccion(pendientes);
    } catch (SinProveedorIADisponibleException sinProveedor) {
      REGISTRO.warn("Ningun proveedor de IA disponible para clasificar titulares; corto el ciclo");
      return;
    }

    int noticiasProcesadasEnCiclo = 0;
    boolean yaSeBuscoUnAnalisis = false;
    for (NoticiaClasificada candidata : candidatas) {
      if (noticiasProcesadasEnCiclo >= propiedadesFeed.cantidadMaximaPorCiclo() || seccionesAbiertas.isEmpty()) {
        break;
      }
      // Las candidatas van de la mas reciente a la mas antigua: la primera de
      // cada seccion abierta es la que se publica.
      if (!seccionesAbiertas.contains(candidata.seccion())) {
        continue;
      }
      NoticiaHardwareRss noticia = candidata.noticia();
      // Un resumen de analisis por ciclo como mucho: cada uno gasta creditos
      // de Tavily, y los demas esperan al siguiente ciclo sin perderse.
      boolean esAnalisis = preparadorMaterial.esAnalisisDeTerceros(noticia);
      if (esAnalisis && yaSeBuscoUnAnalisis) {
        continue;
      }
      yaSeBuscoUnAnalisis = yaSeBuscoUnAnalisis || esAnalisis;

      // Una noticia sin material no gasta cuota de IA ni cuenta para el tope.
      Optional<MaterialListo> material = resolverMaterial(noticia);
      if (material.isEmpty()) {
        continue;
      }
      noticiasProcesadasEnCiclo++;
      ResultadoRedaccion resultado = procesarNoticiaComoArticulo(noticia, material.get());
      if (!resultado.hayCuotaDeIa()) {
        break;
      }
      // Cierro la seccion prevista y, si la redaccion la clasifico en otra,
      // tambien esa: el ritmo cuenta lo que de verdad se publico.
      resultado.seccionPublicada().ifPresent(seccionPublicada -> {
        seccionesAbiertas.remove(candidata.seccion());
        seccionesAbiertas.remove(seccionPublicada);
      });
    }

    REGISTRO.info("Termino la sincronizacion: {} noticias enviadas a la IA", noticiasProcesadasEnCiclo);
  }

  private Optional<MaterialListo> resolverMaterial(NoticiaHardwareRss noticia) {
    return switch (preparadorMaterial.preparar(noticia)) {
      case MaterialListo listo -> Optional.of(listo);
      case SinMaterial sinMaterial -> {
        registrarDescarte(noticia, sinMaterial.motivo());
        yield Optional.empty();
      }
      case Aplazado aplazado -> Optional.empty();
    };
  }

  private void completarFotosPendientes() {
    try {
      servicioFotos.completarFotosPendientes();
    } catch (RuntimeException falloFotos) {
      // Sin foto el articulo usa la portada generada: nunca bloquea el ciclo.
      REGISTRO.warn("No pude completar las fotos pendientes: {}", falloFotos.getMessage());
    }
  }

  private List<NoticiaHardwareRss> obtenerNoticiasDeTodosLosFeeds() {
    List<NoticiaHardwareRss> noticias = new ArrayList<>();
    for (String urlFeed : propiedadesFeed.urlsFeed()) {
      try {
        noticias.addAll(clienteFeed.obtenerNoticiasRecientes(urlFeed));
      } catch (RuntimeException excepcionFeed) {
        // Un feed caido o con XML roto no debe dejar sin contenido a los demas:
        // lo registro y lo reintento en el siguiente ciclo.
        REGISTRO.warn("No pude leer el feed {}: {}", urlFeed, excepcionFeed.getMessage());
      }
    }
    return noticias;
  }

  // Los rechazados por titular no se registran como descartados: si el
  // clasificador se equivoca, la noticia vuelve a evaluarse en el siguiente
  // ciclo en vez de perderse para siempre. Sin clasificacion no hay secciones
  // y el ritmo por seccion no se puede respetar: ese ciclo no publica.
  private List<NoticiaClasificada> clasificarPorSeccion(List<NoticiaHardwareRss> pendientes) {
    if (pendientes.isEmpty()) {
      return List.of();
    }
    Optional<Map<Integer, CategoriaArticulo>> seccionesPorPosicion;
    try {
      seccionesPorPosicion = clasificadorTitulares.clasificarTitulares(
          pendientes.stream().map(NoticiaHardwareRss::titulo).toList());
    } catch (SinProveedorIADisponibleException sinProveedor) {
      // Sin proveedores tampoco se podria redactar: que el ciclo lo corte.
      throw sinProveedor;
    } catch (RuntimeException falloClasificacion) {
      REGISTRO.warn("Fallo la clasificacion de titulares ({}); no publico en este ciclo",
          falloClasificacion.getMessage());
      return List.of();
    }
    if (seccionesPorPosicion.isEmpty()) {
      REGISTRO.warn("La clasificacion de titulares no se pudo interpretar; no publico en este ciclo");
      return List.of();
    }
    Map<Integer, CategoriaArticulo> secciones = seccionesPorPosicion.get();
    List<NoticiaClasificada> candidatas = IntStream.range(0, pendientes.size())
        .filter(secciones::containsKey)
        .mapToObj(posicion -> new NoticiaClasificada(pendientes.get(posicion), secciones.get(posicion)))
        .toList();
    REGISTRO.info("Clasificacion por titular: {} de {} noticias pendientes son de hardware",
        candidatas.size(), pendientes.size());
    return candidatas;
  }

  private boolean esNoticiaYaResuelta(NoticiaHardwareRss noticia) {
    return registroDescartadas.estaDescartada(noticia.identificadorExterno())
        || servicioIngestaArticulos.yaEstaIngerida(noticia.identificadorExterno());
  }

  // hayCuotaDeIa es false si ningun proveedor de la cadena esta disponible:
  // seguir llamando solo suma rechazos, y las noticias que queden se piden en
  // el siguiente ciclo.
  private ResultadoRedaccion procesarNoticiaComoArticulo(NoticiaHardwareRss noticia, MaterialListo material) {
    ResultadoGeneracionContenido resultadoGeneracion;
    try {
      resultadoGeneracion = pipelineGeneracionContenido.generarArticulo(material.solicitud());
    } catch (SinProveedorIADisponibleException sinProveedor) {
      REGISTRO.warn("Ningun proveedor de IA disponible; corto el ciclo en la noticia '{}'", noticia.titulo());
      return new ResultadoRedaccion(false, Optional.empty());
    } catch (RuntimeException excepcionGeneracion) {
      // La generacion con IA es el eslabon mas caro y menos fiable del ciclo
      // (llamada de red a un proveedor externo, parseo de su respuesta) — un
      // fallo aqui no debe tumbar el resto del lote.
      REGISTRO.warn("Fallo generando contenido para la noticia '{}': {}",
          noticia.titulo(), excepcionGeneracion.getMessage());
      return new ResultadoRedaccion(true, Optional.empty());
    }

    return switch (resultadoGeneracion) {
      case ResultadoGeneracionContenido.ContenidoAprobado aprobado ->
          new ResultadoRedaccion(true, persistirArticuloAprobado(noticia, aprobado, material.enlacesFuentes()));
      case ResultadoGeneracionContenido.ContenidoFueraDeTematica fueraDeTematica -> {
        registrarDescarte(noticia, fueraDeTematica.motivo());
        yield new ResultadoRedaccion(true, Optional.empty());
      }
      case ResultadoGeneracionContenido.ContenidoRechazado rechazado -> {
        gestionarRechazo(noticia, material, rechazado.motivo());
        yield new ResultadoRedaccion(true, Optional.empty());
      }
    };
  }

  // Una noticia rechazada se reintenta en el siguiente ciclo, salvo un resumen
  // de analisis: reintentarlo repetiria la busqueda y gastaria creditos.
  private void gestionarRechazo(NoticiaHardwareRss noticia, MaterialListo material, String motivo) {
    REGISTRO.warn("Rechazo el contenido generado para la noticia '{}': {}", noticia.titulo(), motivo);
    if (material.solicitud().esResumenDeAnalisis()) {
      registrarDescarte(noticia, "Resumen de analisis rechazado: " + motivo);
    }
  }

  private void registrarDescarte(NoticiaHardwareRss noticia, String motivo) {
    REGISTRO.info("Descarto la noticia '{}': {}", noticia.titulo(), motivo);
    try {
      registroDescartadas.registrarDescarte(noticia.identificadorExterno(), motivo);
    } catch (RuntimeException excepcionRegistro) {
      // Si no puedo guardarlo, en el peor caso la IA la vuelve a evaluar el
      // proximo ciclo: no justifica cortar el resto del lote.
      REGISTRO.warn("No pude registrar el descarte de la noticia '{}': {}",
          noticia.titulo(), excepcionRegistro.getMessage());
    }
  }

  // Devuelve la seccion del articulo si se ha creado de verdad.
  private Optional<CategoriaArticulo> persistirArticuloAprobado(
      NoticiaHardwareRss noticia, ResultadoGeneracionContenido.ContenidoAprobado aprobado,
      List<String> enlacesFuentes) {
    // Publico el titular que redacta la IA, no noticia.titulo(): el de la
    // fuente llega en ingles y no es texto nuestro.
    DatosFuenteArticulo datosFuente = new DatosFuenteArticulo(
        noticia.identificadorExterno(),
        noticia.enlaceOriginal(),
        aprobado.titulo(),
        aprobado.entradilla(),
        noticia.fechaPublicacion().atOffset(ZoneOffset.UTC));

    ResultadoIngesta resultadoIngesta;
    try {
      resultadoIngesta = servicioIngestaArticulos.ingestarArticulo(
          datosFuente,
          CreditoFuentesArticulo.anadirCreditoFuentes(aprobado.cuerpoHtml(), enlacesFuentes),
          new DatosEditorialesArticulo(
              aprobado.categoria(), aprobado.producto(), aprobado.especificaciones(), aprobado.puntosClave()));
    } catch (RuntimeException excepcionIngesta) {
      REGISTRO.warn("Fallo guardando el articulo de la noticia '{}': {}",
          noticia.titulo(), excepcionIngesta.getMessage());
      return Optional.empty();
    }

    return switch (resultadoIngesta) {
      case ResultadoIngesta.ArticuloCreado creado -> {
        REGISTRO.info("Articulo creado (id={}) para la noticia '{}'", creado.idArticulo(), noticia.titulo());
        yield Optional.ofNullable(aprobado.categoria());
      }
      case ResultadoIngesta.ArticuloYaExistente yaExistente -> {
        REGISTRO.info("La noticia '{}' (idExternoFuente={}) ya estaba ingerida, la omito",
            noticia.titulo(), yaExistente.idExternoFuente());
        yield Optional.empty();
      }
    };
  }

  private record NoticiaClasificada(NoticiaHardwareRss noticia, CategoriaArticulo seccion) {
  }

  private record ResultadoRedaccion(boolean hayCuotaDeIa, Optional<CategoriaArticulo> seccionPublicada) {
  }
}
