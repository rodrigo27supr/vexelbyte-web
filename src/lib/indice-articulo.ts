// Indice "En este articulo" a partir de los <h2> del cuerpo que redacta la IA.
// Les pongo un id en el build (el HTML ya viene saneado del backend y aqui solo
// se anade el atributo), asi el indice enlaza a cada apartado sin JavaScript.

export interface ApartadoIndice {
  id: string;
  titulo: string;
}

const PATRON_SUBTITULO = /<h2(?:\s[^>]*)?>([\s\S]*?)<\/h2>/g;

// jsoup solo escapa estas entidades al serializar; el resto de caracteres
// (tildes, enie) llegan tal cual en UTF-8.
const ENTIDADES_HTML: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#39;': "'",
  '&nbsp;': ' ',
};

function extraerTextoPlano(html: string): string {
  return html
    .replace(/<[^>]+>/g, '')
    .replace(/&(?:amp|lt|gt|quot|#39|nbsp);/g, (entidad) => ENTIDADES_HTML[entidad])
    .trim();
}

function convertirEnIdentificador(titulo: string): string {
  return titulo
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export function construirIndiceArticulo(cuerpoHtml: string): { cuerpoConAnclas: string; apartados: ApartadoIndice[] } {
  const apartados: ApartadoIndice[] = [];
  const identificadoresUsados = new Set<string>();

  const cuerpoConAnclas = cuerpoHtml.replace(PATRON_SUBTITULO, (_subtituloCompleto, contenidoSubtitulo: string) => {
    const titulo = extraerTextoPlano(contenidoSubtitulo);
    // Prefijo propio para no chocar con los id de la plantilla (titulo-puntos-clave...).
    const identificadorBase = `apartado-${convertirEnIdentificador(titulo) || apartados.length + 1}`;
    let identificador = identificadorBase;
    for (let repeticion = 2; identificadoresUsados.has(identificador); repeticion++) {
      identificador = `${identificadorBase}-${repeticion}`;
    }
    identificadoresUsados.add(identificador);
    apartados.push({ id: identificador, titulo });
    return `<h2 id="${identificador}">${contenidoSubtitulo}</h2>`;
  });

  return { cuerpoConAnclas, apartados };
}
