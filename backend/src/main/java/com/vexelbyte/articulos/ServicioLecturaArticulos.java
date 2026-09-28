package com.vexelbyte.articulos;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Lectura publica de articulos y mapeo a DTO. Las listas editoriales se
// guardan como JSON en la entidad y se deserializan aqui, al construir la
// respuesta.
@Service
class ServicioLecturaArticulos {

  private static final TypeReference<List<EspecificacionTecnica>> TIPO_LISTA_ESPECIFICACIONES =
      new TypeReference<>() {
      };
  private static final TypeReference<List<String>> TIPO_LISTA_TEXTOS = new TypeReference<>() {
  };

  private static final int MAXIMO_DATOS_CLAVE = 3;
  private static final int LONGITUD_MAXIMA_NOMBRE_DATO_CLAVE = 14;
  private static final int LONGITUD_MAXIMA_VALOR_DATO_CLAVE = 26;

  // La foto de un articulo no cambia una vez descargada; un dia de cache basta
  // para que builds seguidos no la pidan de nuevo.
  private static final Duration CACHE_FOTOS = Duration.ofDays(1);

  // Un dia cubre varios ciclos: si la foto sigue fallando, la noticia sale
  // con la portada de datos antes que quedarse oculta para siempre.
  private static final Duration ESPERA_MAXIMA_FOTO = Duration.ofDays(1);

  private final ArticuloRepository repositorioArticulos;
  private final FotoArticuloAlmacenadaRepository repositorioFotos;
  private final ObjectMapper mapeadorJson;
  private final Clock reloj;

  ServicioLecturaArticulos(
      ArticuloRepository repositorioArticulos, FotoArticuloAlmacenadaRepository repositorioFotos,
      ObjectMapper mapeadorJson, Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.repositorioFotos = repositorioFotos;
    this.mapeadorJson = mapeadorJson;
    this.reloj = reloj;
  }

  List<ArticuloResumenResponse> listarPublicados() {
    return repositorioArticulos.buscarPublicados(calcularLimiteSinFoto()).stream()
        .map(articulo -> ArticuloResumenResponse.desde(
            articulo, extraerDatosClave(leerListaJson(articulo.getEspecificacionesJson(), TIPO_LISTA_ESPECIFICACIONES))))
        .toList();
  }

  // La IA ordena la ficha por relevancia: la portada de datos muestra las
  // primeras filas que caben en una linea. Con nombres largos ("Velocidad
  // teorica maxima") el valor quedaba cortado en una letra.
  private List<EspecificacionTecnica> extraerDatosClave(List<EspecificacionTecnica> especificaciones) {
    return especificaciones.stream()
        .filter(especificacion -> especificacion.nombre().length() <= LONGITUD_MAXIMA_NOMBRE_DATO_CLAVE)
        .filter(especificacion -> especificacion.valor().length() <= LONGITUD_MAXIMA_VALOR_DATO_CLAVE)
        .limit(MAXIMO_DATOS_CLAVE)
        .toList();
  }

  // Un articulo despublicado da 404 aunque se conozca su slug exacto.
  ResponseEntity<ArticuloDetalleResponse> obtenerPorSlug(String slug) {
    return repositorioArticulos.buscarPublicadoPorSlug(slug, calcularLimiteSinFoto())
        .map(this::construirDetalle)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // Igual que el detalle: la foto de un articulo despublicado da 404.
  ResponseEntity<byte[]> obtenerFoto(String slug) {
    return repositorioArticulos.buscarPublicadoPorSlug(slug, calcularLimiteSinFoto())
        .flatMap(articulo -> repositorioFotos.findById(articulo.getId()))
        .map(foto -> ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
            .cacheControl(CacheControl.maxAge(CACHE_FOTOS).cachePublic())
            .body(foto.getContenido()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private OffsetDateTime calcularLimiteSinFoto() {
    return OffsetDateTime.now(reloj).minus(ESPERA_MAXIMA_FOTO);
  }

  private ArticuloDetalleResponse construirDetalle(Articulo articulo) {
    List<EspecificacionTecnica> especificaciones =
        leerListaJson(articulo.getEspecificacionesJson(), TIPO_LISTA_ESPECIFICACIONES);
    return ArticuloDetalleResponse.desde(
        articulo,
        especificaciones,
        leerListaJson(articulo.getPuntosClaveJson(), TIPO_LISTA_TEXTOS),
        extraerDatosClave(especificaciones));
  }

  private <ElementoLista> List<ElementoLista> leerListaJson(
      String json, TypeReference<List<ElementoLista>> tipoLista) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    return mapeadorJson.readValue(json, tipoLista);
  }
}
