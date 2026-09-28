package com.vexelbyte.automatizacion.gsmarena;

// Lo que saco de la pagina de una noticia de GSMArena: el texto completo del
// cuerpo (el RSS solo trae un extracto recortado) y la ficha tecnica del
// dispositivo que enlaza, nula si no enlaza ninguno.
public record MaterialNoticiaGsmarena(String textoCuerpo, String fichaTecnica) {
}
