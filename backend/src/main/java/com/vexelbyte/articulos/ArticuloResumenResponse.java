package com.vexelbyte.articulos;

import java.time.OffsetDateTime;
import java.util.List;

// DTO de salida para el listado publico -- deliberadamente sin cuerpoHtml:
// una tarjeta de listado no necesita el articulo completo. Categoria,
// producto, foto y datos clave si viajan: la tarjeta los usa para su portada.
public record ArticuloResumenResponse(
    String slug,
    String titulo,
    String descripcion,
    OffsetDateTime fechaPublicacion,
    String autor,
    String categoria,
    String producto,
    FotoArticuloResponse foto,
    List<EspecificacionTecnica> datosClave) {

  static ArticuloResumenResponse desde(Articulo articulo, List<EspecificacionTecnica> datosClave) {
    return new ArticuloResumenResponse(
        articulo.getSlug(),
        articulo.getTitulo(),
        articulo.getDescripcion(),
        articulo.getFechaPublicacion(),
        articulo.getAutor(),
        articulo.getCategoria() != null ? articulo.getCategoria().name() : null,
        articulo.getProducto(),
        FotoArticuloResponse.desde(articulo),
        datosClave);
  }
}
