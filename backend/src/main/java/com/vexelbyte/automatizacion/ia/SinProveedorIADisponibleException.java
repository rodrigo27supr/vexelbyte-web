package com.vexelbyte.automatizacion.ia;

// Todos los eslabones de la cadena han fallado o estan suspendidos por cuota:
// el job corta el ciclo al recibirla en vez de seguir gastando peticiones.
public class SinProveedorIADisponibleException extends RuntimeException {

  public SinProveedorIADisponibleException(String mensaje) {
    super(mensaje);
  }
}
