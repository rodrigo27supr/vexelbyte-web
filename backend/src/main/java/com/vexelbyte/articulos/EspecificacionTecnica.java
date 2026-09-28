package com.vexelbyte.articulos;

// Una fila de la ficha tecnica del articulo ("Pantalla" / "6,77 pulgadas
// AMOLED a 144 Hz"). Se guarda como JSON y el frontend la pinta como tabla.
public record EspecificacionTecnica(String nombre, String valor) {
}
