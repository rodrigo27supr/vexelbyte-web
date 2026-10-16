package com.vexelbyte.automatizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import com.vexelbyte.automatizacion.config.PropiedadesReconstruccionWeb;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

// Contra un servidor HTTP real en local, para comprobar de verdad que sale un POST.
class NotificadorReconstruccionWebTest {

  private final List<String> peticionesRecibidas = new CopyOnWriteArrayList<>();
  private HttpServer servidorDeVercel;

  @AfterEach
  void pararServidor() {
    if (servidorDeVercel != null) {
      servidorDeVercel.stop(0);
    }
  }

  private String levantarServidorQueResponde(int estado) throws IOException {
    servidorDeVercel = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    servidorDeVercel.createContext("/v1/integrations/deploy/prj_prueba/secreto", intercambio -> {
      peticionesRecibidas.add(intercambio.getRequestMethod());
      intercambio.sendResponseHeaders(estado, -1);
      intercambio.close();
    });
    servidorDeVercel.start();
    return "http://localhost:" + servidorDeVercel.getAddress().getPort() + "/v1/integrations/deploy/prj_prueba/secreto";
  }

  private NotificadorReconstruccionWeb crearNotificador(String urlDeployHook) {
    return new NotificadorReconstruccionWeb(RestClient.create(), new PropiedadesReconstruccionWeb(urlDeployHook));
  }

  @Test
  void enviaUnPostAlDeployHook() throws IOException {
    NotificadorReconstruccionWeb notificador = crearNotificador(levantarServidorQueResponde(201));

    notificador.solicitarReconstruccion();

    assertThat(peticionesRecibidas).containsExactly("POST");
  }

  @Test
  void sinDeployHookNoLlamaANadieNiFalla() throws IOException {
    levantarServidorQueResponde(201);

    assertThatCode(() -> crearNotificador("").solicitarReconstruccion()).doesNotThrowAnyException();
    assertThatCode(() -> crearNotificador(null).solicitarReconstruccion()).doesNotThrowAnyException();

    assertThat(peticionesRecibidas).isEmpty();
  }

  @Test
  void unRechazoDeVercelNoTumbaElCiclo() throws IOException {
    NotificadorReconstruccionWeb notificador = crearNotificador(levantarServidorQueResponde(429));

    assertThatCode(notificador::solicitarReconstruccion).doesNotThrowAnyException();
    assertThat(peticionesRecibidas).containsExactly("POST");
  }

  @Test
  void unServidorCaidoNoTumbaElCiclo() {
    // Puerto cerrado: nadie escucha.
    NotificadorReconstruccionWeb notificador = crearNotificador("http://localhost:1/v1/integrations/deploy/prj_prueba/secreto");

    assertThatCode(notificador::solicitarReconstruccion).doesNotThrowAnyException();
  }
}
