package com.vexelbyte.automatizacion.ia;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

// Encadena proveedores de IA gratuitos: si uno agota su cuota o falla, la
// noticia pasa al siguiente en vez de quedarse sin redactar hasta el proximo
// ciclo. El pipeline solo ve un ClienteModeloIA mas.
public class CadenaProveedoresIA implements ClienteModeloIA {

  private static final Logger REGISTRO = LoggerFactory.getLogger(CadenaProveedoresIA.class);

  // Tras un 429 aparto el eslabon una hora: la cuota por minuto se habra
  // recuperado y, si era la diaria, no merece la pena preguntar en cada noticia.
  private static final Duration SUSPENSION_POR_CUOTA_AGOTADA = Duration.ofHours(1);

  private final List<ClienteModeloIA> eslabones;
  private final Map<ClienteModeloIA, Instant> suspendidosHasta = new ConcurrentHashMap<>();

  public CadenaProveedoresIA(List<ClienteModeloIA> eslabones) {
    this.eslabones = List.copyOf(eslabones);
  }

  @Override
  public String generarTexto(String promptSistema, String promptUsuario) {
    return ejecutarEnCadena(eslabon -> eslabon.generarTexto(promptSistema, promptUsuario));
  }

  // Comparte suspensiones con el texto: la cuota de un modelo es la misma
  // tanto si le paso una imagen como si no.
  @Override
  public String generarTextoConImagen(String promptSistema, String promptUsuario, ImagenParaIA imagen) {
    return ejecutarEnCadena(eslabon -> eslabon.generarTextoConImagen(promptSistema, promptUsuario, imagen));
  }

  @Override
  public String describirProveedor() {
    return "cadena de " + eslabones.size() + " proveedores";
  }

  private String ejecutarEnCadena(Function<ClienteModeloIA, String> llamadaAlProveedor) {
    for (ClienteModeloIA eslabon : eslabones) {
      if (estaSuspendido(eslabon)) {
        continue;
      }
      try {
        String textoGenerado = llamadaAlProveedor.apply(eslabon);
        REGISTRO.info("Contenido generado con {}", eslabon.describirProveedor());
        return textoGenerado;
      } catch (UnsupportedOperationException sinSoporteDeImagenes) {
        // No es un fallo del proveedor: simplemente no ve imagenes.
      } catch (HttpClientErrorException.TooManyRequests cuotaAgotada) {
        suspendidosHasta.put(eslabon, Instant.now().plus(SUSPENSION_POR_CUOTA_AGOTADA));
        REGISTRO.warn("{} ha agotado su cuota; lo aparto {} y paso al siguiente",
            eslabon.describirProveedor(), SUSPENSION_POR_CUOTA_AGOTADA);
      } catch (RestClientException | IllegalStateException falloProveedor) {
        // Caida, timeout, modelo retirado (404) o respuesta vacia: no suspendo,
        // porque puede ser puntual, pero esta noticia la intenta el siguiente.
        REGISTRO.warn("{} ha fallado ({}); paso al siguiente proveedor",
            eslabon.describirProveedor(), falloProveedor.getMessage());
      }
    }
    throw new SinProveedorIADisponibleException(
        "Ningun proveedor de IA disponible: todos han fallado o agotado su cuota");
  }

  private boolean estaSuspendido(ClienteModeloIA eslabon) {
    Instant suspendidoHasta = suspendidosHasta.get(eslabon);
    return suspendidoHasta != null && Instant.now().isBefore(suspendidoHasta);
  }
}
