package com.vexelbyte.automatizacion.imagenes;

import com.vexelbyte.articulos.FotoArticuloAlmacenada;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Obtiene la imagen principal de la noticia de origen, la misma que el medio
// eligio para ilustrarla. Si es material de prensa del fabricante lo decide
// despues ClasificadorImagenesIA; aqui solo se localiza y se descarga.
@Component
public class ClienteImagenFuente {

  // GSMArena sirve en og:image una version recortada con su logotipo encima
  // ("/-952x498w6/"); quitando ese segmento se obtiene la imagen original.
  private static final Pattern SEGMENTO_TAMANO_GSMARENA = Pattern.compile("/-\\d+x\\d+w\\d+/");

  private final RestClient clienteHttp;

  public ClienteImagenFuente(@Qualifier("clienteHttpFeedHardware") RestClient clienteHttp) {
    this.clienteHttp = clienteHttp;
  }

  public Optional<String> obtenerUrlImagenPrincipal(String enlaceNoticia) {
    String html = clienteHttp.get().uri(URI.create(enlaceNoticia)).retrieve().body(String.class);
    Document paginaNoticia = Jsoup.parse(html == null ? "" : html, enlaceNoticia);
    String urlImagen = paginaNoticia.select("meta[property=og:image]").attr("abs:content");
    if (urlImagen.isBlank() || !urlImagen.startsWith("https://")) {
      return Optional.empty();
    }
    return Optional.of(SEGMENTO_TAMANO_GSMARENA.matcher(urlImagen).replaceFirst("/"));
  }

  // La descarga el backend, con nuestro User-Agent, y no el build de Vercel:
  // un fallo de un tercero durante el build dejaria la web sin actualizar.
  public ImagenDescargada descargarImagen(String urlImagen) {
    ResponseEntity<byte[]> respuesta = clienteHttp.get().uri(URI.create(urlImagen)).retrieve().toEntity(byte[].class);
    MediaType tipoContenido = respuesta.getHeaders().getContentType();
    byte[] contenido = respuesta.getBody();
    if (tipoContenido == null || !"image".equals(tipoContenido.getType())) {
      throw new IllegalStateException("La fuente no devolvio una imagen: " + tipoContenido);
    }
    if (contenido == null || contenido.length == 0 || contenido.length > FotoArticuloAlmacenada.TAMANO_MAXIMO_BYTES) {
      throw new IllegalStateException("Imagen vacia o demasiado grande: " + (contenido == null ? 0 : contenido.length));
    }
    int[] dimensiones = leerDimensiones(contenido);
    return new ImagenDescargada(
        contenido, tipoContenido.getType() + "/" + tipoContenido.getSubtype(), dimensiones[0], dimensiones[1]);
  }

  // Leo solo la cabecera, sin decodificar los pixeles: con los 256 MB de heap
  // de Render, decodificar una foto grande entera podria tumbar el proceso.
  // ImageIO entiende JPEG y PNG; otro formato se descarta en vez de publicarse
  // sin dimensiones.
  private int[] leerDimensiones(byte[] contenido) {
    try (ImageInputStream flujoImagen = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
      Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujoImagen);
      if (!lectores.hasNext()) {
        throw new IllegalStateException("Formato de imagen no reconocido");
      }
      ImageReader lector = lectores.next();
      try {
        lector.setInput(flujoImagen);
        return new int[] {lector.getWidth(0), lector.getHeight(0)};
      } finally {
        lector.dispose();
      }
    } catch (IOException imagenCorrupta) {
      throw new IllegalStateException("No pude leer la imagen: " + imagenCorrupta.getMessage(), imagenCorrupta);
    }
  }
}
