package com.vexelbyte.automatizacion.imagenes;

import com.vexelbyte.automatizacion.config.PropiedadesPexels;
import com.vexelbyte.automatizacion.imagenes.RespuestaBusquedaPexels.FotoPexels;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Busca en Pexels una foto que ilustre el tema del articulo cuando la noticia
// de origen no trae material oficial. Su licencia permite usarlas gratis
// citando al fotografo, y sus fotos de tecnologia son de calidad profesional.
@Component
public class ClientePexels {

  // Varias noticias piden la misma busqueda ("graphics card"): con varios
  // candidatos puedo saltarme las fotos que ya usa otro articulo.
  private static final int CANDIDATOS_POR_BUSQUEDA = 15;

  private final RestClient clienteHttp;
  private final PropiedadesPexels propiedadesPexels;

  public ClientePexels(@Qualifier("clienteHttpPexels") RestClient clienteHttp, PropiedadesPexels propiedadesPexels) {
    this.clienteHttp = clienteHttp;
    this.propiedadesPexels = propiedadesPexels;
  }

  public boolean estaConfigurado() {
    return propiedadesPexels.tieneClave();
  }

  // Pido la version "landscape" (1200x627): ya viene con la forma de los
  // marcos de la web, asi que encaja sin recortar ni rellenar.
  public Optional<FotoIlustrativa> buscarFoto(String consulta, Predicate<String> estaYaUsada) {
    RespuestaBusquedaPexels respuesta = clienteHttp.get()
        .uri(constructorUri -> constructorUri
            .path("/v1/search")
            .queryParam("query", consulta)
            .queryParam("orientation", "landscape")
            .queryParam("per_page", CANDIDATOS_POR_BUSQUEDA)
            .build())
        .header(HttpHeaders.AUTHORIZATION, propiedadesPexels.apiKey())
        .retrieve()
        .body(RespuestaBusquedaPexels.class);
    if (respuesta == null || respuesta.photos() == null) {
      return Optional.empty();
    }
    return respuesta.photos().stream()
        .filter(Objects::nonNull)
        .filter(foto -> foto.src() != null && foto.src().landscape() != null && foto.url() != null)
        .filter(foto -> !estaYaUsada.test(foto.src().landscape()))
        .findFirst()
        .map(this::convertirEnFotoIlustrativa);
  }

  private FotoIlustrativa convertirEnFotoIlustrativa(FotoPexels foto) {
    String fotografo = foto.photographer() == null || foto.photographer().isBlank()
        ? "Fotógrafo de Pexels"
        : foto.photographer().strip();
    return new FotoIlustrativa(foto.src().landscape(), fotografo, foto.url());
  }
}
