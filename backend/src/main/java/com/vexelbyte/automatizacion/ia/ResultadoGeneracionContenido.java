package com.vexelbyte.automatizacion.ia;

import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import java.util.List;

// Modelo el resultado como un tipo sellado en vez de devolver un booleano "es
// valido" mas un texto que a veces es HTML y a veces un motivo de rechazo --
// asi el compilador obliga a quien consuma esto a manejar todos los casos, no
// hay forma de leer cuerpoHtml por error cuando el contenido fue rechazado.
// Separo FueraDeTematica de Rechazado porque el primero es un veredicto
// editorial definitivo (no merece reintento) y el segundo un fallo tecnico
// que si puede resolverse en el siguiente ciclo.
public sealed interface ResultadoGeneracionContenido {

  // categoria y producto pueden ser nulos; las listas nunca, como mucho vacias.
  record ContenidoAprobado(
      String titulo,
      String entradilla,
      String cuerpoHtml,
      CategoriaArticulo categoria,
      String producto,
      List<EspecificacionTecnica> especificaciones,
      List<String> puntosClave) implements ResultadoGeneracionContenido {
  }

  record ContenidoFueraDeTematica(String motivo) implements ResultadoGeneracionContenido {
  }

  record ContenidoRechazado(String motivo) implements ResultadoGeneracionContenido {
  }
}
