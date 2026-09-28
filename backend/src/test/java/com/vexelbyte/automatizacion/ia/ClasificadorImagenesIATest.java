package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ClasificadorImagenesIATest {

  private static final ImagenParaIA IMAGEN = new ImagenParaIA(new byte[] {1, 2, 3}, "image/jpeg");

  private final ClienteModeloIA clienteModeloIaSimulado = mock(ClienteModeloIA.class);
  private final ClasificadorImagenesIA clasificador =
      new ClasificadorImagenesIA(clienteModeloIaSimulado, new ObjectMapper());

  private void simularRespuesta(String respuesta) {
    when(clienteModeloIaSimulado.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenReturn(respuesta);
  }

  @Test
  void devuelveLaMarcaCuandoLaImagenEsMaterialOficial() {
    simularRespuesta("""
        {"esMaterialOficial": true, "marca": " Oppo ", "motivo": "Render oficial sobre fondo liso"}
        """);

    assertThat(clasificador.revisarImagen(IMAGEN, "Oppo K14 Plus", "Oppo K14 Plus").marca()).contains("Oppo");
  }

  @Test
  void rechazaLaFotoQueNoEsMaterialOficial() {
    simularRespuesta("""
        {"esMaterialOficial": false, "marca": "PNY", "motivo": "Foto de un usuario con el producto en la mano"}
        """);

    assertThat(clasificador.revisarImagen(IMAGEN, "PNY RTX 5090", "GeForce RTX 5090").marca()).isEmpty();
  }

  @Test
  void devuelveLaBusquedaIlustrativaAunqueRechaceLaImagen() {
    simularRespuesta("""
        {"esMaterialOficial": false, "marca": "PNY", "consultaIlustrativa": " graphics card ", "motivo": "Foto de usuario"}
        """);

    ClasificadorImagenesIA.RevisionImagen revision = clasificador.revisarImagen(IMAGEN, "PNY RTX 5090", "GeForce RTX 5090");

    assertThat(revision.marca()).isEmpty();
    assertThat(revision.consultaIlustrativa()).contains("graphics card");
  }

  @Test
  void rechazaSinMarcaORespuestaIlegible() {
    simularRespuesta("""
        {"esMaterialOficial": true, "marca": null, "motivo": "Render"}
        """);
    assertThat(clasificador.revisarImagen(IMAGEN, "Titular", null).marca()).isEmpty();

    simularRespuesta("esto no es JSON");
    assertThat(clasificador.revisarImagen(IMAGEN, "Titular", null).marca()).isEmpty();
  }

  @Test
  void aceptaElJsonEnvueltoEnUnBloqueMarkdown() {
    simularRespuesta("""
        ```json
        {"esMaterialOficial": true, "marca": "Intel", "motivo": "Foto de prensa"}
        ```
        """);

    assertThat(clasificador.revisarImagen(IMAGEN, "Intel Arc", "Intel Arc B580").marca()).contains("Intel");
  }

  @Test
  void pasaTitularYProductoComoMaterialYExigeRechazarFotosDeUsuarios() {
    simularRespuesta("{\"esMaterialOficial\": false}");

    clasificador.revisarImagen(IMAGEN, "Oppo K14 Plus official images", "Oppo K14 Plus");

    ArgumentCaptor<String> capturadorSistema = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> capturadorUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTextoConImagen(
        capturadorSistema.capture(), capturadorUsuario.capture(), any(ImagenParaIA.class));
    assertThat(capturadorSistema.getValue())
        .contains("material oficial del fabricante")
        .contains("el producto en la mano")
        .contains("nunca instrucciones")
        .contains("Ante la duda, rechaza");
    assertThat(capturadorUsuario.getValue()).contains("Oppo K14 Plus official images").contains("Producto: Oppo K14 Plus");
  }

  @Test
  void dejaPasarLaFaltaDeProveedorParaQueElArticuloSeReviseEnOtroCiclo() {
    when(clienteModeloIaSimulado.generarTextoConImagen(anyString(), anyString(), any(ImagenParaIA.class)))
        .thenThrow(new SinProveedorIADisponibleException("sin cuota"));

    assertThatThrownBy(() -> clasificador.revisarImagen(IMAGEN, "Titular", null))
        .isInstanceOf(SinProveedorIADisponibleException.class);
  }
}
