package com.vexelbyte.automatizacion.persistencia;

import com.vexelbyte.articulos.Articulo;
import com.vexelbyte.articulos.ArticuloRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// Unico punto de escritura de articulos generados por el motor de
// automatizacion. Deliberadamente no expone ningun metodo para actualizar o
// borrar: el pipeline solo crea contenido nuevo, no toca lo que ya existe.
@Service
public class ServicioIngestaArticulos {

  // La columna descripcion es VARCHAR(500) en la base (V1__esquema_inicial.sql)
  // -- recorto en vez de arriesgarme a que el insert falle por un titulo de
  // noticia inusualmente largo.
  private static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

  // 12 caracteres hex (48 bits): una colision entre noticias es despreciable
  // y, si ocurriera, la restriccion UNIQUE del slug la frena sin corromper nada.
  private static final int LONGITUD_SUFIJO_SLUG = 12;

  private static final String AUTOR_AUTOMATIZADO = "Equipo VexelByte";

  private final ArticuloRepository repositorioArticulos;
  private final Clock reloj;
  private final ObjectMapper mapeadorJson;

  public ServicioIngestaArticulos(ArticuloRepository repositorioArticulos, ObjectMapper mapeadorJson, Clock reloj) {
    this.repositorioArticulos = repositorioArticulos;
    this.reloj = reloj;
    this.mapeadorJson = mapeadorJson;
  }

  // Lo consulta el job ANTES de llamar a la IA: el feed devuelve las mismas
  // noticias en cada ciclo y no quiero pagar una generacion por una que ya
  // esta guardada.
  @Transactional(readOnly = true)
  public boolean yaEstaIngerida(String idExternoFuente) {
    return repositorioArticulos.existsByIdExternoFuente(idExternoFuente);
  }

  // Uso @Transactional para que la comprobacion de idempotencia y el guardado
  // sean una sola unidad atomica: si algo fallara entre medias (por ejemplo,
  // una violacion de constraint que no capturo explicitamente en el codigo),
  // no quiero que quede un articulo a medio guardar -- todo o nada, nunca un
  // estado intermedio inconsistente.
  @Transactional
  public ResultadoIngesta ingestarArticulo(
      DatosFuenteArticulo datosFuente, String cuerpoHtmlAprobado, DatosEditorialesArticulo datosEditoriales) {
    // Comprobacion explicita ANTES del insert, no un catch de violacion de
    // constraint unico. Asi el caso esperado (el job procesando la misma
    // noticia una segunda vez) es una decision de negocio clara desde el
    // primer momento, no un efecto secundario de capturar una excepcion de
    // base de datos.
    if (repositorioArticulos.existsByIdExternoFuente(datosFuente.idExternoFuente())) {
      return new ResultadoIngesta.ArticuloYaExistente(datosFuente.idExternoFuente());
    }

    Articulo articulo = new Articulo();
    articulo.setIdExternoFuente(datosFuente.idExternoFuente());
    articulo.setEnlaceFuente(datosFuente.enlaceFuente());
    articulo.setTitulo(datosFuente.titulo());
    articulo.setDescripcion(recortarDescripcion(datosFuente.descripcion()));
    articulo.setSlug(generarSlugUnico(datosFuente.titulo(), datosFuente.idExternoFuente()));
    articulo.setCuerpoHtml(cuerpoHtmlAprobado);
    articulo.setFechaPublicacion(datosFuente.fechaPublicacion());
    articulo.setFechaCreacion(OffsetDateTime.now(reloj));
    articulo.setAutor(AUTOR_AUTOMATIZADO);
    // Publicacion automatica por decision del Tech Lead (2026-09-26): el
    // contenido llega aqui ya filtrado por tematica y con el HTML sanitizado,
    // y una cola manual dejaba el sitio sin actualizar. borrador sigue
    // existiendo para despublicar a mano un articulo concreto.
    articulo.setBorrador(false);
    articulo.setCategoria(datosEditoriales.categoria());
    articulo.setProducto(datosEditoriales.producto());
    articulo.setEspecificacionesJson(mapeadorJson.writeValueAsString(datosEditoriales.especificaciones()));
    articulo.setPuntosClaveJson(mapeadorJson.writeValueAsString(datosEditoriales.puntosClave()));

    Articulo articuloGuardado = repositorioArticulos.save(articulo);
    return new ResultadoIngesta.ArticuloCreado(articuloGuardado.getId());
  }

  private String recortarDescripcion(String descripcion) {
    return descripcion.length() > LONGITUD_MAXIMA_DESCRIPCION
        ? descripcion.substring(0, LONGITUD_MAXIMA_DESCRIPCION)
        : descripcion;
  }

  private String generarSlugUnico(String titulo, String idExternoFuente) {
    String slugBase = Normalizer.normalize(titulo, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");

    // El sufijo garantiza unicidad sin contador ni reintento del insert. Uso un
    // hash del idExternoFuente y no el id tal cual: el guid de GSMArena es una
    // URL completa y sus barras rompian la ruta /noticias/[slug] del frontend.
    return slugBase + "-" + calcularSufijoUnico(idExternoFuente);
  }

  private String calcularSufijoUnico(String idExternoFuente) {
    try {
      byte[] resumenSha256 = MessageDigest.getInstance("SHA-256")
          .digest(idExternoFuente.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(resumenSha256).substring(0, LONGITUD_SUFIJO_SLUG);
    } catch (NoSuchAlgorithmException algoritmoNoDisponible) {
      // Toda JVM esta obligada por especificacion a incluir SHA-256.
      throw new IllegalStateException(algoritmoNoDisponible);
    }
  }
}
