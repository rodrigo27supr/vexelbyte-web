package com.vexelbyte.articulos;

import static org.assertj.core.api.Assertions.assertThat;

import com.vexelbyte.articulos.CacheFotosArticulos.FotoServida;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CacheFotosArticulosTest {

  private static final int CATORCE_MEGABYTES = 14 * 1024 * 1024;

  private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-16T08:00:00Z"));
  private final CacheFotosArticulos cacheFotos = new CacheFotosArticulos(reloj);

  @Test
  void devuelveLaFotoGuardadaHastaQueCaduca() {
    cacheFotos.guardar("oppo-k14-plus", new FotoServida(new byte[] {1}, "image/jpeg"));

    reloj.avanzarMinutos(59);
    assertThat(cacheFotos.obtener("oppo-k14-plus")).isPresent();

    reloj.avanzarMinutos(2);
    assertThat(cacheFotos.obtener("oppo-k14-plus")).isEmpty();
  }

  @Test
  void expulsaLaFotoMenosUsadaAlSuperarElLimiteDeMemoria() {
    cacheFotos.guardar("primera", new FotoServida(new byte[CATORCE_MEGABYTES], "image/jpeg"));
    cacheFotos.guardar("segunda", new FotoServida(new byte[CATORCE_MEGABYTES], "image/jpeg"));
    // Usar la primera la libra de ser la expulsada.
    cacheFotos.obtener("primera");
    cacheFotos.guardar("tercera", new FotoServida(new byte[CATORCE_MEGABYTES], "image/jpeg"));

    assertThat(cacheFotos.obtener("segunda")).isEmpty();
    assertThat(cacheFotos.obtener("primera")).isPresent();
    assertThat(cacheFotos.obtener("tercera")).isPresent();
  }

  @Test
  void sustituirUnaFotoNoDuplicaSuPeso() {
    // Si el peso se acumulara al sustituir, la tercera pasada expulsaria fotos sin necesidad.
    for (int pasada = 0; pasada < 5; pasada++) {
      cacheFotos.guardar("repetida", new FotoServida(new byte[CATORCE_MEGABYTES], "image/jpeg"));
    }
    cacheFotos.guardar("otra", new FotoServida(new byte[CATORCE_MEGABYTES], "image/jpeg"));

    assertThat(cacheFotos.obtener("repetida")).isPresent();
    assertThat(cacheFotos.obtener("otra")).isPresent();
  }

  private static final class RelojAjustable extends Clock {

    private Instant instanteActual;

    RelojAjustable(Instant instanteInicial) {
      this.instanteActual = instanteInicial;
    }

    void avanzarMinutos(long minutos) {
      instanteActual = instanteActual.plusSeconds(minutos * 60);
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
