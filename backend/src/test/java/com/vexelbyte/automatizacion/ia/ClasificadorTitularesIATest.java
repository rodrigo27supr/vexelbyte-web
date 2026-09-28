package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.articulos.CategoriaArticulo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ClasificadorTitularesIATest {

  private final ClienteModeloIA clienteModeloIaSimulado = mock(ClienteModeloIA.class);
  private final ClasificadorTitularesIA clasificador =
      new ClasificadorTitularesIA(clienteModeloIaSimulado, new ObjectMapper());

  private static final List<String> TITULARES = List.of("GTA 6 vende millones", "RTX 5090 review", "Oppo K14 Plus");

  private void simularRespuesta(String respuesta) {
    when(clienteModeloIaSimulado.generarTexto(anyString(), anyString())).thenReturn(respuesta);
  }

  @Test
  void devuelveLaSeccionDeCadaTitularAprobadoPorSuPosicionDesdeCero() {
    simularRespuesta("""
        {"aprobados": [{"indice": 2, "seccion": "HARDWARE_PC"}, {"indice": 3, "seccion": "moviles"}]}
        """);

    assertThat(clasificador.clasificarTitulares(TITULARES))
        .contains(Map.of(1, CategoriaArticulo.HARDWARE_PC, 2, CategoriaArticulo.MOVILES));
  }

  @Test
  void numeraLosTitularesDesdeUnoYPideLaSeccion() {
    simularRespuesta("{\"aprobados\": []}");

    clasificador.clasificarTitulares(TITULARES);

    ArgumentCaptor<String> capturadorSistema = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> capturadorPromptUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorSistema.capture(), capturadorPromptUsuario.capture());
    assertThat(capturadorPromptUsuario.getValue())
        .contains("1. GTA 6 vende millones")
        .contains("2. RTX 5090 review")
        .contains("3. Oppo K14 Plus");
    assertThat(capturadorSistema.getValue())
        .contains("HARDWARE_PC").contains("MOVILES").contains("PERIFERICOS").contains("RENDIMIENTO");
  }

  @Test
  void ignoraIndicesFueraDeRangoYSeccionesDesconocidas() {
    simularRespuesta("""
        ```json
        {"aprobados": [{"indice": 0, "seccion": "MOVILES"}, {"indice": 2, "seccion": "HARDWARE_PC"},
         {"indice": 9, "seccion": "MOVILES"}, {"indice": 3, "seccion": "VIDEOJUEGOS"}]}
        ```
        """);

    assertThat(clasificador.clasificarTitulares(TITULARES)).contains(Map.of(1, CategoriaArticulo.HARDWARE_PC));
  }

  @Test
  void devuelveVacioCuandoLaRespuestaNoSePuedeInterpretar() {
    simularRespuesta("no es JSON");

    assertThat(clasificador.clasificarTitulares(TITULARES)).isEqualTo(Optional.empty());
  }

  @Test
  void devuelveVacioCuandoFaltaLaLista() {
    simularRespuesta("{\"otraCosa\": true}");

    assertThat(clasificador.clasificarTitulares(TITULARES)).isEmpty();
  }
}
