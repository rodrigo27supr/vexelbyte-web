package com.vexelbyte.automatizacion.imagenes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Las fotos son lo que llena la base de datos gratuita (0,5 GB): sin comprimir
// rondaban los 170 KB y algun PNG de prensa pasaba de 1 MB. Las guardo al
// ancho maximo que usa la web y en JPEG, en torno a 60-80 KB.
@Component
public class CompresorImagenes {

  private static final Logger REGISTRO = LoggerFactory.getLogger(CompresorImagenes.class);

  // La cabecera del articulo ocupa 1.104 px y Astro sirve como mucho 1.200:
  // 1.600 deja margen para pantallas de alta densidad.
  private static final int ANCHO_MAXIMO = 1600;
  private static final float CALIDAD_JPEG = 0.82f;

  // Con 256 MB de heap en Render, una foto de 8.000 px decodificada entera
  // ocuparia cientos de MB: la leo ya reducida si pasa de este ancho.
  private static final int ANCHO_MAXIMO_DECODIFICADO = 3200;

  // Si falla la compresion guardo la original: pesa mas, pero la foto no se pierde.
  public ImagenDescargada comprimir(ImagenDescargada original) {
    try {
      BufferedImage leida = leerReducida(original.contenido());
      int anchoFinal = Math.min(ANCHO_MAXIMO, leida.getWidth());
      int altoFinal = Math.round((float) leida.getHeight() * anchoFinal / leida.getWidth());
      byte[] comprimida = escribirJpeg(redimensionar(leida, anchoFinal, altoFinal));
      // Una imagen ya optimizada puede crecer al pasar a JPEG: me quedo con la menor.
      if (comprimida.length >= original.contenido().length) {
        return original;
      }
      return new ImagenDescargada(comprimida, "image/jpeg", anchoFinal, altoFinal);
    } catch (IOException | RuntimeException falloCompresion) {
      REGISTRO.warn("No pude comprimir una foto ({}); guardo la original", falloCompresion.getMessage());
      return original;
    }
  }

  private BufferedImage leerReducida(byte[] contenido) throws IOException {
    try (ImageInputStream flujoImagen = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
      Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujoImagen);
      if (!lectores.hasNext()) {
        throw new IOException("Formato de imagen no reconocido");
      }
      ImageReader lector = lectores.next();
      try {
        lector.setInput(flujoImagen);
        int submuestreo = Math.max(1, (int) Math.ceil((double) lector.getWidth(0) / ANCHO_MAXIMO_DECODIFICADO));
        ImageReadParam parametrosLectura = lector.getDefaultReadParam();
        parametrosLectura.setSourceSubsampling(submuestreo, submuestreo, 0, 0);
        return lector.read(0, parametrosLectura);
      } finally {
        lector.dispose();
      }
    }
  }

  // Fondo blanco explicito: JPEG no tiene transparencia, y un PNG de prensa
  // con fondo transparente saldria negro.
  private BufferedImage redimensionar(BufferedImage leida, int ancho, int alto) {
    BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = destino.createGraphics();
    try {
      lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      lienzo.setColor(Color.WHITE);
      lienzo.fillRect(0, 0, ancho, alto);
      lienzo.drawImage(leida, 0, 0, ancho, alto, null);
    } finally {
      lienzo.dispose();
    }
    return destino;
  }

  private byte[] escribirJpeg(BufferedImage imagen) throws IOException {
    ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    try (ImageOutputStream flujoSalida = ImageIO.createImageOutputStream(salida)) {
      ImageWriteParam parametrosEscritura = escritor.getDefaultWriteParam();
      parametrosEscritura.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parametrosEscritura.setCompressionQuality(CALIDAD_JPEG);
      escritor.setOutput(flujoSalida);
      escritor.write(null, new IIOImage(imagen, null, null), parametrosEscritura);
    } finally {
      escritor.dispose();
    }
    return salida.toByteArray();
  }
}
