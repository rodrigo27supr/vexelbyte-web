package com.vexelbyte.automatizacion.busqueda;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// include_domains vacio no se envia: Tavily lo interpretaria como "ningun dominio".
record SolicitudBusquedaTavily(
    String query,
    String topic,
    @JsonProperty("search_depth") String profundidadBusqueda,
    @JsonProperty("chunks_per_source") int fragmentosPorFuente,
    @JsonProperty("max_results") int maximoResultados,
    @JsonProperty("time_range") String periodo,
    @JsonProperty("exclude_domains") List<String> dominiosExcluidos,
    @JsonProperty("include_domains") @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> dominiosIncluidos) {
}
