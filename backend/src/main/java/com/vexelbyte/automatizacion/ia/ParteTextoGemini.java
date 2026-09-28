package com.vexelbyte.automatizacion.ia;

// Gemini repite esta misma forma ({"text": "..."}) tanto en el cuerpo de la
// solicitud (instruccion de sistema y contenido del usuario) como dentro de
// la respuesta — la comparto en un unico record en vez de duplicarla.
record ParteTextoGemini(String text) implements ParteSolicitudGemini {
}
