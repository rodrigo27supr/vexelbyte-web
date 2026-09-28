package com.vexelbyte.articulos;

// Foto del articulo y todo lo necesario para citarla. Solo viaja si el
// articulo la tiene. url es relativa a la API: la foto la sirve el backend.
// esIlustrativa distingue la foto de banco de imagenes del material oficial,
// porque el pie de foto tiene que decirlo.
public record FotoArticuloResponse(
    String url,
    int ancho,
    int alto,
    String autor,
    String licencia,
    String urlLicencia,
    String urlOrigen,
    boolean esIlustrativa) {

  static FotoArticuloResponse desde(Articulo articulo) {
    if (articulo.getFotoUrl() == null) {
      return null;
    }
    return new FotoArticuloResponse(
        "/api/articulos/" + articulo.getSlug() + "/foto",
        articulo.getFotoAncho(),
        articulo.getFotoAlto(),
        articulo.getFotoAutor(),
        articulo.getFotoLicencia(),
        articulo.getFotoUrlLicencia(),
        articulo.getFotoUrlOrigen(),
        articulo.isFotoEsIlustrativa());
  }
}
