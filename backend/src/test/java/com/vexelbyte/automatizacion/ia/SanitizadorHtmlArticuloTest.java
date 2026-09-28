package com.vexelbyte.automatizacion.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizadorHtmlArticuloTest {

  private final SanitizadorHtmlArticulo sanitizador = new SanitizadorHtmlArticulo();

  @Test
  void conservaElMarcadoEditorialPermitido() {
    String htmlLegitimo = "<article><h2>Rendimiento</h2><p>La <strong>RTX</strong> rinde <em>mas</em>.</p>"
        + "<ul><li>4K</li></ul><table><thead><tr><th>Juego</th></tr></thead>"
        + "<tbody><tr><td>120 FPS</td></tr></tbody></table><blockquote>Cita</blockquote></article>";

    assertThat(sanitizador.limpiarHtml(htmlLegitimo)).isEqualTo(htmlLegitimo);
  }

  @Test
  void eliminaScriptsEstilosIframesEImagenes() {
    String htmlMalicioso = "<p>Texto</p><script>alert(1)</script><style>body{}</style>"
        + "<iframe src=\"https://ataque.example\"></iframe><img src=\"x\" onerror=\"alert(1)\">";

    assertThat(sanitizador.limpiarHtml(htmlMalicioso)).isEqualTo("<p>Texto</p>");
  }

  @Test
  void eliminaManejadoresDeEventosYAtributosNoPermitidos() {
    String htmlConAtributos = "<p onclick=\"robar()\" style=\"color:red\" class=\"x\" id=\"y\">Texto</p>";

    assertThat(sanitizador.limpiarHtml(htmlConAtributos)).isEqualTo("<p>Texto</p>");
  }

  @Test
  void soloConservaEnlacesHttpYHttpsYLesFuerzaRelNofollow() {
    String htmlConEnlaces = "<p><a href=\"https://fuente.example/noticia\" target=\"_blank\">bueno</a>"
        + "<a href=\"javascript:alert(1)\">malo</a><a href=\"data:text/html,x\">malo</a></p>";

    assertThat(sanitizador.limpiarHtml(htmlConEnlaces)).isEqualTo(
        "<p><a href=\"https://fuente.example/noticia\" rel=\"nofollow noopener noreferrer\">bueno</a>"
            + "<a rel=\"nofollow noopener noreferrer\">malo</a><a rel=\"nofollow noopener noreferrer\">malo</a></p>");
  }

  @Test
  void detectaCuandoNoQuedaTextoVisible() {
    assertThat(sanitizador.tieneTextoVisible("<article><p> </p></article>")).isFalse();
    assertThat(sanitizador.tieneTextoVisible("<p>Hola</p>")).isTrue();
  }
}
