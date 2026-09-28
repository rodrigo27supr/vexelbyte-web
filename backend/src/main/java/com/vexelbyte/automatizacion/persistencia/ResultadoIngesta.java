package com.vexelbyte.automatizacion.persistencia;

// Sellada por la misma razon que ResultadoGeneracionContenido: quien llame a
// ServicioIngestaArticulos tiene que decidir explicitamente que hacer en
// cada caso (loguear creacion vs loguear duplicado), el compilador no deja
// que se trate un duplicado como si fuera una creacion por descuido.
public sealed interface ResultadoIngesta {

  record ArticuloCreado(Long idArticulo) implements ResultadoIngesta {
  }

  record ArticuloYaExistente(String idExternoFuente) implements ResultadoIngesta {
  }
}
