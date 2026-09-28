package com.vexelbyte.automatizacion.busqueda;

// Lo que un medio ha publicado sobre el producto: el medio es el dominio,
// que es lo que el articulo cita al atribuirle cada valoracion.
public record FragmentoAnalisis(String medio, String titulo, String url, String texto) {
}
