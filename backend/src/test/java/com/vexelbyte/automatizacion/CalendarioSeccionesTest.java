package com.vexelbyte.automatizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.articulos.ArticuloRepository;
import com.vexelbyte.articulos.CategoriaArticulo;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarioSeccionesTest {

  private final ArticuloRepository repositorioArticulos = mock(ArticuloRepository.class);

  private CalendarioSecciones crearCalendarioEn(String instanteUtc) {
    return new CalendarioSecciones(repositorioArticulos, Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC));
  }

  @Test
  void elPrimerDiaDePublicacionAbreLasSeccionesQueAunNoHanPublicadoEseDia() {
    when(repositorioArticulos.existsByCategoriaAndFechaCreacionAfter(any(), any())).thenReturn(false);
    when(repositorioArticulos.existsByCategoriaAndFechaCreacionAfter(eq(CategoriaArticulo.MOVILES), any()))
        .thenReturn(true);

    // 06:05 UTC del 28 de septiembre son las 08:05 en Madrid (horario de verano).
    assertThat(crearCalendarioEn("2026-09-28T06:05:00Z").obtenerSeccionesAbiertas()).containsExactlyInAnyOrder(
        CategoriaArticulo.HARDWARE_PC, CategoriaArticulo.PERIFERICOS, CategoriaArticulo.RENDIMIENTO);
  }

  @Test
  void cuentaLoPublicadoDesdeLaMedianocheDeMadrid() {
    ArgumentCaptor<OffsetDateTime> capturadorInicio = ArgumentCaptor.forClass(OffsetDateTime.class);
    when(repositorioArticulos.existsByCategoriaAndFechaCreacionAfter(any(), capturadorInicio.capture()))
        .thenReturn(false);

    crearCalendarioEn("2026-09-28T06:05:00Z").obtenerSeccionesAbiertas();

    assertThat(capturadorInicio.getValue().toInstant()).isEqualTo(Instant.parse("2026-09-27T22:00:00Z"));
  }

  @Test
  void noPublicaAntesDeLasOchoDeLaMananaDeMadrid() {
    // 07:30 en Madrid.
    assertThat(crearCalendarioEn("2026-09-28T05:30:00Z").obtenerSeccionesAbiertas()).isEmpty();
    verify(repositorioArticulos, never()).existsByCategoriaAndFechaCreacionAfter(any(), any());
  }

  @Test
  void noPublicaElDiaSiguienteNiAntesDelInicioDelCalendario() {
    assertThat(crearCalendarioEn("2026-09-29T10:00:00Z").obtenerSeccionesAbiertas()).isEmpty();
    assertThat(crearCalendarioEn("2026-09-27T10:00:00Z").obtenerSeccionesAbiertas()).isEmpty();
  }

  @Test
  void publicaEnDiasAlternosTambienConElHorarioDeInvierno() {
    when(repositorioArticulos.existsByCategoriaAndFechaCreacionAfter(any(), any())).thenReturn(false);

    // 29 de noviembre: 62 dias desde el inicio. 07:05 UTC son las 08:05 en Madrid en invierno.
    assertThat(crearCalendarioEn("2026-11-29T07:05:00Z").obtenerSeccionesAbiertas())
        .isEqualTo(EnumSet.allOf(CategoriaArticulo.class));
    assertThat(crearCalendarioEn("2026-11-29T06:05:00Z").obtenerSeccionesAbiertas()).isEmpty();
  }
}
