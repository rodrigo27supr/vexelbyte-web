package com.vexelbyte.automatizacion.persistencia;

import com.vexelbyte.articulos.CategoriaArticulo;
import com.vexelbyte.articulos.EspecificacionTecnica;
import java.util.List;

// Lo que redacta la IA ademas del cuerpo, ya validado por
// PipelineGeneracionContenido. categoria y producto pueden ser nulos; las
// listas nunca, como mucho vacias.
public record DatosEditorialesArticulo(
    CategoriaArticulo categoria,
    String producto,
    List<EspecificacionTecnica> especificaciones,
    List<String> puntosClave) {
}
