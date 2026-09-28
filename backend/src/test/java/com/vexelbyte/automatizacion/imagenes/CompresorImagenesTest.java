package com.vexelbyte.automatizacion.imagenes;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CompresorImagenesTest {

  private final CompresorImagenes compresor = new CompresorImagenes();

  // Ruido para que el PNG pese de verdad, como una foto: un color plano se
  // comprime solo y no probaria nada.
  private static byte[] crearPngConRuido(int ancho, int alto, boolean conTransparencia) throws IOException {
    BufferedImage imagen = new BufferedImage(
        ancho, alto, conTransparencia ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Random generador = new Random(42);
    for (int fila = 0; fila < alto; fila++) {
      for (int columna = 0; columna < ancho; columna++) {
        imagen.setRGB(columna, fila, conTransparencia && columna < ancho / 2 ? 0x00000000 : 0xFF000000 | generador.nextInt(0xFFFFFF));
      }
    }
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(imagen, "png", salida);
    return salida.toByteArray();
  }

  @Test
  void reduceUnaFotoGrandeAlAnchoMaximoEnJpegYPesaMenos() throws IOException {
    byte[] original = crearPngConRuido(2400, 1200, false);

    ImagenDescargada comprimida = compresor.comprimir(new ImagenDescargada(original, "image/png", 2400, 1200));

    assertThat(comprimida.tipoContenido()).isEqualTo("image/jpeg");
    assertThat(comprimida.ancho()).isEqualTo(1600);
    assertThat(comprimida.alto()).isEqualTo(800);
    assertThat(comprimida.contenido().length).isLessThan(original.length);
    assertThat(ImageIO.read(new ByteArrayInputStream(comprimida.contenido())).getWidth()).isEqualTo(1600);
  }

  @Test
  void pintaDeBlancoLaTransparenciaEnVezDeNegro() throws IOException {
    byte[] original = crearPngConRuido(1200, 600, true);

    ImagenDescargada comprimida = compresor.comprimir(new ImagenDescargada(original, "image/png", 1200, 600));

    BufferedImage leida = ImageIO.read(new ByteArrayInputStream(comprimida.contenido()));
    Color esquinaTransparente = new Color(leida.getRGB(10, 10));
    assertThat(esquinaTransparente.getRed()).isGreaterThan(240);
    assertThat(esquinaTransparente.getGreen()).isGreaterThan(240);
    assertThat(esquinaTransparente.getBlue()).isGreaterThan(240);
  }

  @Test
  void conservaLaOriginalSiComprimirlaNoLaHaceMasLigera() throws IOException {
    BufferedImage lisa = new BufferedImage(900, 500, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = lisa.createGraphics();
    lienzo.setColor(Color.WHITE);
    lienzo.fillRect(0, 0, 900, 500);
    lienzo.dispose();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(lisa, "png", salida);
    ImagenDescargada original = new ImagenDescargada(salida.toByteArray(), "image/png", 900, 500);

    assertThat(compresor.comprimir(original)).isSameAs(original);
  }

  @Test
  void devuelveLaOriginalSiNoEsUnaImagenLegible() {
    ImagenDescargada ilegible = new ImagenDescargada(new byte[] {1, 2, 3}, "image/jpeg", 1200, 600);

    assertThat(compresor.comprimir(ilegible)).isSameAs(ilegible);
  }
}
