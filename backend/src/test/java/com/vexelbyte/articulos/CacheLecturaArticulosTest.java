package com.vexelbyte.articulos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheLecturaArticulosTest {

  private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-16T08:00:00Z"));
  private final CacheLecturaArticulos cacheLectura = new CacheLecturaArticulos(reloj);

  private static ArticuloResumenResponse resumenDe(String slug) {
    return new ArticuloResumenResponse(
        slug, "Titulo", "Descripcion", OffsetDateTime.parse("2026-10-15T00:00:00Z"), "Equipo", null, null, null, List.of());
  }

  private static ArticuloDetalleResponse detalleDe(String slug) {
    return new ArticuloDetalleResponse(
        slug, "Titulo", "Descripcion", "<p>Cuerpo</p>", OffsetDateTime.parse("2026-10-15T00:00:00Z"), null, "Equipo", null,
        null, List.of(), List.of(), null, List.of());
  }

  @Test
  void laListaCaducaALasSeisHoras() {
    cacheLectura.guardarLista(List.of(resumenDe("a")));

    reloj.avanzarHoras(5);
    assertThat(cacheLectura.obtenerLista()).isPresent();

    reloj.avanzarHoras(2);
    assertThat(cacheLectura.obtenerLista()).isEmpty();
  }

  @Test
  void elDetalleCaducaAlDia() {
    cacheLectura.guardarDetalle("a", detalleDe("a"));

    reloj.avanzarHoras(23);
    assertThat(cacheLectura.obtenerDetalle("a")).isPresent();

    reloj.avanzarHoras(2);
    assertThat(cacheLectura.obtenerDetalle("a")).isEmpty();
  }

  @Test
  void soloRechazaUnSlugSinPreguntarALaBaseSiTieneLaListaVigente() {
    assertThat(cacheLectura.sabeQueNoEstaPublicado("inventado")).isFalse();

    cacheLectura.guardarLista(List.of(resumenDe("existe")));
    assertThat(cacheLectura.sabeQueNoEstaPublicado("existe")).isFalse();
    assertThat(cacheLectura.sabeQueNoEstaPublicado("inventado")).isTrue();

    // Con la lista caducada ya no hay certeza: que lo decida la base.
    reloj.avanzarHoras(7);
    assertThat(cacheLectura.sabeQueNoEstaPublicado("inventado")).isFalse();
  }

  @Test
  void invalidarVaciaLaListaLosDetallesYLasFotos() {
    cacheLectura.guardarLista(List.of(resumenDe("a")));
    cacheLectura.guardarDetalle("a", detalleDe("a"));
    cacheLectura.fotos().guardar("a", new CacheFotosArticulos.FotoServida(new byte[] {1}, "image/jpeg"));

    cacheLectura.invalidar();

    assertThat(cacheLectura.obtenerLista()).isEmpty();
    assertThat(cacheLectura.obtenerDetalle("a")).isEmpty();
    assertThat(cacheLectura.fotos().obtener("a")).isEmpty();
    // Sin lista vigente no se rechaza nada sin consultar la base.
    assertThat(cacheLectura.sabeQueNoEstaPublicado("cualquiera")).isFalse();
  }

  @Test
  void guardaComoMucho1000DetallesYExpulsaElMenosUsado() {
    for (int posicion = 0; posicion <= 1000; posicion++) {
      cacheLectura.guardarDetalle("articulo-" + posicion, detalleDe("articulo-" + posicion));
    }

    assertThat(cacheLectura.obtenerDetalle("articulo-0")).isEmpty();
    assertThat(cacheLectura.obtenerDetalle("articulo-1")).isPresent();
    assertThat(cacheLectura.obtenerDetalle("articulo-1000")).isPresent();
  }

  private static final class RelojAjustable extends Clock {

    private Instant instanteActual;

    RelojAjustable(Instant instanteInicial) {
      this.instanteActual = instanteInicial;
    }

    void avanzarHoras(long horas) {
      instanteActual = instanteActual.plusSeconds(horas * 3600);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
      return this;
    }

    @Override
    public Instant instant() {
      return instanteActual;
    }
  }
}
