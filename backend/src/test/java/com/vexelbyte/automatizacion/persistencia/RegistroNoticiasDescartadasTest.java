package com.vexelbyte.automatizacion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegistroNoticiasDescartadasTest {

  private final NoticiaDescartadaRepository repositorioSimulado = mock(NoticiaDescartadaRepository.class);
  private final RegistroNoticiasDescartadas registro = new RegistroNoticiasDescartadas(repositorioSimulado);

  @Test
  void guardaElDescarteConSuMotivoYFecha() {
    registro.registrarDescarte("guid-1", "Es una nota de parche");

    ArgumentCaptor<NoticiaDescartada> capturador = ArgumentCaptor.forClass(NoticiaDescartada.class);
    verify(repositorioSimulado).save(capturador.capture());
    assertThat(capturador.getValue().getIdExternoFuente()).isEqualTo("guid-1");
    assertThat(capturador.getValue().getMotivo()).isEqualTo("Es una nota de parche");
    assertThat(capturador.getValue().getFechaDescarte()).isNotNull();
  }

  @Test
  void recortaElMotivoAlTamanoDeLaColumna() {
    registro.registrarDescarte("guid-1", "m".repeat(800));

    ArgumentCaptor<NoticiaDescartada> capturador = ArgumentCaptor.forClass(NoticiaDescartada.class);
    verify(repositorioSimulado).save(capturador.capture());
    assertThat(capturador.getValue().getMotivo()).hasSize(500);
  }

  @Test
  void consultaSiUnaNoticiaYaEstaDescartada() {
    when(repositorioSimulado.existsById("guid-1")).thenReturn(true);

    assertThat(registro.estaDescartada("guid-1")).isTrue();
    assertThat(registro.estaDescartada("guid-2")).isFalse();
  }
}
