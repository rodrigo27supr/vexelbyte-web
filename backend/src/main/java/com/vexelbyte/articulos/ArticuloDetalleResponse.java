package com.vexelbyte.articulos;

import java.time.OffsetDateTime;
import java.util.List;

// DTO de salida para la vista individual: incluye cuerpoHtml, la ficha
// tecnica y los puntos clave, que Astro inyecta en la pagina estatica.
public record ArticuloDetalleResponse(
    String slug,
    String titulo,
    String descripcion,
    String cuerpoHtml,
    OffsetDateTime fechaPublicacion,
    OffsetDateTime fechaActualizacion,
    String autor,
    String categoria,
    String producto,
    // Listas vacias, nunca nulas, en articulos sin datos editoriales: el
    // frontend solo tiene que comprobar si hay elementos.
    List<EspecificacionTecnica> especificaciones,
    List<String> puntosClave,
    FotoArticuloResponse foto,
    // Mismo criterio que en el listado: la cabecera sin foto usa la portada de datos.
    List<EspecificacionTecnica> datosClave) {

  static ArticuloDetalleResponse desde(
      Articulo articulo,
      List<EspecificacionTecnica> especificaciones,
      List<String> puntosClave,
      List<EspecificacionTecnica> datosClave) {
    return new ArticuloDetalleResponse(
        articulo.getSlug(),
        articulo.getTitulo(),
        articulo.getDescripcion(),
        articulo.getCuerpoHtml(),
        articulo.getFechaPublicacion(),
        articulo.getFechaActualizacion(),
        articulo.getAutor(),
        articulo.getCategoria() != null ? articulo.getCategoria().name() : null,
        articulo.getProducto(),
        especificaciones,
        puntosClave,
        FotoArticuloResponse.desde(articulo),
        datosClave);
  }
}
