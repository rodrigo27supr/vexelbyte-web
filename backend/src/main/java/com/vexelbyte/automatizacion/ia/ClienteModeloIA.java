package com.vexelbyte.automatizacion.ia;

// Abstraccion sobre un proveedor de IA concreto. Cada proveedor tiene su
// propio formato de request/response, asi que va en su propia clase; la
// CadenaProveedoresIA las encadena sin que el pipeline sepa cual respondio.
public interface ClienteModeloIA {

  // Devuelve el texto crudo que responde el modelo, sin ningun procesamiento.
  // Quien la llama decide que formato exigir en el prompt (HTML, JSON, etc.)
  // y como interpretar el resultado — esta interfaz no asume nada sobre eso.
  String generarTexto(String promptSistema, String promptUsuario);

  // Solo algunos proveedores ven imagenes: los que no, lo declaran y la cadena
  // pasa al siguiente sin contarlo como fallo.
  default String generarTextoConImagen(String promptSistema, String promptUsuario, ImagenParaIA imagen) {
    throw new UnsupportedOperationException(describirProveedor() + " no admite imagenes");
  }

  // Nombre legible para los logs (gemini/gemini-3.6-flash, groq/...): sin el,
  // cuando falla un eslabon de la cadena no se sabe cual ha sido.
  String describirProveedor();
}
