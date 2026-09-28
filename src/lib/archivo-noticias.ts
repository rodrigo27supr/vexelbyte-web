import type { ArticuloResumen } from './api-articulos';

// Con el ritmo de un articulo por seccion cada dos dias entran unas 60 noticias
// al mes: 24 por pagina son dos meses por pagina y una carga ligera en movil.
export const NOTICIAS_POR_PAGINA = 24;

export function contarPaginasArchivo(noticias: ArticuloResumen[]): number {
  return Math.max(1, Math.ceil(noticias.length / NOTICIAS_POR_PAGINA));
}

export function obtenerNoticiasDePagina(noticias: ArticuloResumen[], numeroPagina: number): ArticuloResumen[] {
  const inicio = (numeroPagina - 1) * NOTICIAS_POR_PAGINA;
  return noticias.slice(inicio, inicio + NOTICIAS_POR_PAGINA);
}

// El archivo general (/noticias/) y el de cada seccion (/categoria/moviles/)
// se paginan igual. La primera pagina vive en la ruta base y no en
// .../pagina/1/, para no tener dos URL con el mismo contenido.
export function construirRutaPaginaArchivo(rutaBase: string, numeroPagina: number): string {
  return numeroPagina === 1 ? rutaBase : `${rutaBase}pagina/${numeroPagina}/`;
}
