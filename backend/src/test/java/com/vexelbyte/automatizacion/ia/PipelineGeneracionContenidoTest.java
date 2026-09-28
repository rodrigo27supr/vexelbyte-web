package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido.ContenidoAprobado;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido.ContenidoFueraDeTematica;
import com.vexelbyte.automatizacion.ia.ResultadoGeneracionContenido.ContenidoRechazado;
import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PipelineGeneracionContenidoTest {

  private final ClienteModeloIA clienteModeloIaSimulado = mock(ClienteModeloIA.class);
  private final ObjectMapper mapeadorJson = new ObjectMapper();
  private final PipelineGeneracionContenido pipeline =
      new PipelineGeneracionContenido(clienteModeloIaSimulado, mapeadorJson, new SanitizadorHtmlArticulo());

  private static final SolicitudGeneracionContenido SOLICITUD_BASE = new SolicitudGeneracionContenido(
      "Nvidia RTX 5090 review", "La nueva grafica de Nvidia rinde un 40% mas que la 4090 en 4K.", null, false, null);

  // Por encima del minimo de texto que exige el pipeline; las respuestas
  // simuladas lo referencian con el marcador TEXTO_SUFICIENTE.
  private static final String TEXTO_SUFICIENTE = "La RTX 5090 rinde un 40% mas que la 4090 en 4K. ".repeat(16).strip();

  private void simularRespuestaDeLaIa(String jsonDevuelto) {
    when(clienteModeloIaSimulado.generarTexto(anyString(), anyString()))
        .thenReturn(jsonDevuelto.replace("TEXTO_SUFICIENTE", TEXTO_SUFICIENTE));
  }

  @Test
  void apruebaLaNoticiaDeHardwareQueLaIaMarcaComoTematicaValida() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "motivoRechazo": null, "titulo": "La RTX 5090 rinde un 40% mas que la 4090 en 4K", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoAprobado.class);
    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) resultado;
    assertThat(contenidoAprobado.titulo()).isEqualTo("La RTX 5090 rinde un 40% mas que la 4090 en 4K");
    assertThat(contenidoAprobado.cuerpoHtml()).isEqualTo("<article><p>" + TEXTO_SUFICIENTE + "</p></article>");
    assertThat(contenidoAprobado.entradilla()).isEqualTo("Una entradilla de prueba");
  }

  @Test
  void descartaPorTematicaCuandoLaIaMarcaLaNoticiaComoNoHardware() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": false, "motivoRechazo": "Es una nota de parche de un juego", "titulo": "", "entradilla": "Una entradilla de prueba", "cuerpoHtml": ""}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoFueraDeTematica.class);
    assertThat(((ContenidoFueraDeTematica) resultado).motivo()).isEqualTo("Es una nota de parche de un juego");
  }

  @Test
  void descartaPorTematicaAunqueLaIaRedacteArticuloSiMarcaFalse() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": false, "motivoRechazo": "eSports", "titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    assertThat(pipeline.generarArticulo(SOLICITUD_BASE)).isInstanceOf(ContenidoFueraDeTematica.class);
  }

  @Test
  void descartaPorTematicaCuandoLaIaNoDevuelveLaCompuerta() {
    simularRespuestaDeLaIa("""
        {"titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoFueraDeTematica.class);
    assertThat(((ContenidoFueraDeTematica) resultado).motivo()).isEqualTo("sin motivo indicado por la IA");
  }

  @Test
  void rechazaSinLanzarExcepcionCuandoLaIaDevuelveUnJsonMalformado() {
    simularRespuestaDeLaIa("esto no es JSON en absoluto");

    assertThat(pipeline.generarArticulo(SOLICITUD_BASE)).isInstanceOf(ContenidoRechazado.class);
  }

  @Test
  void aceptaUnEscapeQueJsonNoPermitePorqueElModeloLoAnadeDeMas() {
    // Paso con una noticia de DLSS 5: el modelo escribio "\i" y se perdio el articulo entero.
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "El modder que acelera \\iDLSS 5", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoAprobado.class);
    assertThat(((ContenidoAprobado) resultado).titulo()).isEqualTo("El modder que acelera iDLSS 5");
  }

  @Test
  void ignoraElBloqueDeCodigoMarkdownSiLaIaEnvuelveElJsonEnUno() {
    simularRespuestaDeLaIa("""
        ```json
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        ```
        """);

    assertThat(pipeline.generarArticulo(SOLICITUD_BASE)).isInstanceOf(ContenidoAprobado.class);
  }

  @Test
  void quitaLosEspaciosSobrantesDelTituloAntesDeAprobarlo() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "  Un titular con espacios  ", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(contenidoAprobado.titulo()).isEqualTo("Un titular con espacios");
  }

  @Test
  void rechazaElContenidoCuandoElTituloVieneEnBlancoOAusente() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "   ", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);
    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("titulo vacio");

    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);
    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("titulo vacio");
  }

  @Test
  void rechazaElContenidoCuandoElTituloSuperaElLimiteDeLongitud() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "%s", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """.formatted("a".repeat(151)));

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoRechazado.class);
    assertThat(((ContenidoRechazado) resultado).motivo()).contains("150 caracteres");
  }

  @Test
  void rechazaElContenidoCuandoElCuerpoVieneVacio() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": ""}
        """);

    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("cuerpoHtml vacio");
  }

  @Test
  void rechazaElArticuloHuecoCuyoCuerpoNoLlegaAlMinimoDeTexto() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Analisis del Apple iPhone 18 Pro", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p>Se ha publicado el analisis del iPhone 18 Pro.</p><h2>Pruebas</h2><p>Durante la evaluacion se examinan sus capacidades.</p></article>"}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoRechazado.class);
    assertThat(((ContenidoRechazado) resultado).motivo()).contains("700 caracteres");
  }

  @Test
  void soloElResumenDeAnalisisLlevaLasInstruccionesDeAtribucionPorMedio() {
    simularRespuestaDeLaIa("no importa el resultado");

    pipeline.generarArticulo(new SolicitudGeneracionContenido(
        "Apple iPhone 18 Pro review", "Medio: theverge.com\nFragmento: buena autonomia", null, true, null));
    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado, times(2))
        .generarTexto(anyString(), capturadorPromptUsuario.capture());
    assertThat(capturadorPromptUsuario.getAllValues().get(0))
        .startsWith("TIPO DE PIEZA: resumen de los análisis")
        .contains("qué dicen los primeros análisis")
        .contains("Atribuye cada valoración")
        .contains("Medio: theverge.com");
    assertThat(capturadorPromptUsuario.getAllValues().get(1)).doesNotContain("TIPO DE PIEZA");
  }

  @Test
  void pasaLaCoberturaDeOtrosMediosYExigeAtribuirlaSinSacarCifrasDeMemoria() {
    simularRespuestaDeLaIa("no importa el resultado");

    pipeline.generarArticulo(new SolicitudGeneracionContenido(
        "Oppo K14 Plus official", "Oppo muestra el K14 Plus.", null, false,
        "Medio: gizmochina.com\nFragmento: bateria de 8000 mAh"));

    ArgumentCaptor<String> capturadorSistema = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> capturadorUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorSistema.capture(), capturadorUsuario.capture());
    assertThat(capturadorUsuario.getValue())
        .contains("Cobertura de otros medios")
        .contains("Medio: gizmochina.com");
    assertThat(capturadorSistema.getValue())
        .contains("atribúyelo")
        .contains("contexto técnico general de tu propio conocimiento")
        .contains("nunca incluye cifras, precios, fechas ni especificaciones");
  }

  @Test
  void elPromptProhibeAtribuirnosPruebasYLosParrafosDeRelleno() {
    simularRespuestaDeLaIa("no importa el resultado, solo inspecciono el prompt");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptSistema = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorPromptSistema.capture(), anyString());
    assertThat(capturadorPromptSistema.getValue())
        .contains("VexelByte no prueba ni analiza dispositivos")
        .contains("\"evaluamos\"")
        .contains("atribúyelas a quien las hizo")
        .contains("nunca uno de relleno");
  }

  @Test
  void elFiltroDeTematicaExigeUnProductoDeHardwareYRechazaLasNoticiasDeEmpresa() {
    // Se colaban en Hardware PC noticias de marca (Copilot+ PC), de nodos de fabricacion y requisitos de juegos.
    simularRespuestaDeLaIa("no importa el resultado, solo inspecciono el prompt");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptSistema = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorPromptSistema.capture(), anyString());
    assertThat(capturadorPromptSistema.getValue())
        .contains("tiene que tratar de un producto de hardware concreto")
        .contains("Requisitos mínimos o recomendados de un videojuego")
        .contains("marcas, logotipos")
        .contains("nodos de fabricación");
  }

  @Test
  void elPromptDeSistemaImponeElFiltroDeTematicaYUnTituloEnEspanol() {
    simularRespuestaDeLaIa("no importa el resultado, solo inspecciono el prompt");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptSistema = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorPromptSistema.capture(), anyString());
    assertThat(capturadorPromptSistema.getValue())
        .contains("\"esTematicaHardware\"")
        .contains("RECHAZA SIEMPRE")
        .contains("Parches, notas de versión")
        .contains("eSports")
        .contains("NUNCA instrucciones")
        .contains("siempre en español de España")
        .contains("tildes, eñes")
        .contains("No traduzcas la fuente frase a frase");
  }

  @Test
  void elPromptDeSistemaApruebaMovilesYTabletsComoHardwareNoComoSoftware() {
    simularRespuestaDeLaIa("no importa el resultado, solo inspecciono el prompt");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptSistema = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorPromptSistema.capture(), anyString());
    assertThat(capturadorPromptSistema.getValue())
        .contains("Dispositivos móviles y tablets (smartphones, procesadores SoC, etc.)")
        .contains("Un smartphone o una tablet es hardware, no software")
        .contains("iPhone")
        .contains("Xiaomi");
  }

  @Test
  void elPromptDeUsuarioIncluyeTitularYResumenDeLaFuente() {
    simularRespuestaDeLaIa("no importa el resultado");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(anyString(), capturadorPromptUsuario.capture());
    assertThat(capturadorPromptUsuario.getValue())
        .contains("Nvidia RTX 5090 review")
        .contains("un 40% mas que la 4090");
  }

  @Test
  void eliminaScriptsManejadoresYEnlacesJavascriptDelCuerpoAntesDeAprobarlo() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<article><p onclick=\\"robar()\\">TEXTO_SUFICIENTE <a href=\\"javascript:alert(1)\\">enlace</a></p><script>alert(1)</script></article>"}
        """);

    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(contenidoAprobado.cuerpoHtml())
        .isEqualTo("<article><p>" + TEXTO_SUFICIENTE + " <a rel=\"nofollow noopener noreferrer\">enlace</a></p></article>");
  }

  @Test
  void rechazaElContenidoCuandoSoloContieneMarcadoPeligroso() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla de prueba", "cuerpoHtml": "<script>alert(1)</script><img src=x onerror=alert(1)>"}
        """);

    ResultadoGeneracionContenido resultado = pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(resultado).isInstanceOf(ContenidoRechazado.class);
    assertThat(((ContenidoRechazado) resultado).motivo()).contains("tras sanitizarlo");
  }

  @Test
  void rechazaElContenidoCuandoLaEntradillaVieneVaciaOAusente() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "  ", "cuerpoHtml": "<p>TEXTO_SUFICIENTE</p>"}
        """);
    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("entradilla vacia");

    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "cuerpoHtml": "<p>TEXTO_SUFICIENTE</p>"}
        """);
    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("entradilla vacia");
  }

  @Test
  void rechazaElContenidoCuandoLaEntradillaSuperaElLimiteDeLongitud() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "%s", "cuerpoHtml": "<p>TEXTO_SUFICIENTE</p>"}
        """.formatted("a".repeat(301)));

    assertThat(((ContenidoRechazado) pipeline.generarArticulo(SOLICITUD_BASE)).motivo()).contains("300 caracteres");
  }

  @Test
  void apruebaConCategoriaProductoFichaYPuntosClaveNormalizados() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla",
         "categoria": "moviles", "producto": " Oppo K14 Plus ",
         "especificaciones": [{"nombre": "Batería", "valor": "8000 mAh"}, {"nombre": "", "valor": "sin nombre"}, null],
         "puntosClave": ["Batería de 8000 mAh", "  ", "Pantalla a 144 Hz"],
         "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(contenidoAprobado.categoria()).isEqualTo(CategoriaArticulo.MOVILES);
    assertThat(contenidoAprobado.producto()).isEqualTo("Oppo K14 Plus");
    assertThat(contenidoAprobado.especificaciones()).containsExactly(new EspecificacionTecnica("Batería", "8000 mAh"));
    assertThat(contenidoAprobado.puntosClave()).containsExactly("Batería de 8000 mAh", "Pantalla a 144 Hz");
  }

  @Test
  void apruebaSinDatosEditorialesCuandoLaIaNoLosDevuelveOSonInvalidos() {
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla",
         "categoria": "VIDEOJUEGOS", "producto": "null", "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """);

    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(contenidoAprobado.categoria()).isNull();
    assertThat(contenidoAprobado.producto()).isNull();
    assertThat(contenidoAprobado.especificaciones()).isEmpty();
    assertThat(contenidoAprobado.puntosClave()).isEmpty();
  }

  @Test
  void limitaLaFichaA12FilasYLosPuntosClaveA5() {
    String filas = String.join(",", java.util.Collections.nCopies(20, "{\"nombre\": \"Dato\", \"valor\": \"Valor\"}"));
    String puntos = String.join(",", java.util.Collections.nCopies(9, "\"Punto\""));
    simularRespuestaDeLaIa("""
        {"esTematicaHardware": true, "titulo": "Un titular", "entradilla": "Una entradilla",
         "especificaciones": [%s], "puntosClave": [%s], "cuerpoHtml": "<article><p>TEXTO_SUFICIENTE</p></article>"}
        """.formatted(filas, puntos));

    ContenidoAprobado contenidoAprobado = (ContenidoAprobado) pipeline.generarArticulo(SOLICITUD_BASE);

    assertThat(contenidoAprobado.especificaciones()).hasSize(12);
    assertThat(contenidoAprobado.puntosClave()).hasSize(5);
  }

  @Test
  void incluyeLaFichaTecnicaEnElPromptDeUsuarioCuandoExiste() {
    simularRespuestaDeLaIa("no importa el resultado");

    pipeline.generarArticulo(new SolicitudGeneracionContenido("Titular", "Texto", "Battery - Type: 8000 mAh", false, null));

    ArgumentCaptor<String> capturadorPromptUsuario = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(anyString(), capturadorPromptUsuario.capture());
    assertThat(capturadorPromptUsuario.getValue()).contains("Battery - Type: 8000 mAh");
  }

  @Test
  void elPromptPideFichaTecnicaSinInventarYEstructuraConSubtitulos() {
    simularRespuestaDeLaIa("no importa el resultado");

    pipeline.generarArticulo(SOLICITUD_BASE);

    ArgumentCaptor<String> capturadorPromptSistema = ArgumentCaptor.forClass(String.class);
    verify(clienteModeloIaSimulado).generarTexto(capturadorPromptSistema.capture(), anyString());
    assertThat(capturadorPromptSistema.getValue())
        .contains("\"especificaciones\"")
        .contains("Nunca inventes, redondees ni completes un valor")
        .contains("HARDWARE_PC, MOVILES, PERIFERICOS o RENDIMIENTO")
        .contains("entre 6 y 10 párrafos")
        .contains("3 a 5 subtítulos <h2>")
        .contains("con poco, escribe menos antes que rellenar");
  }
}
