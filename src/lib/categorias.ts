import type { ArticuloResumen, CategoriaArticulo } from './api-articulos';

// Catalogo de lineas editoriales: etiqueta visible, ruta de su pagina y
// descripcion para SEO. Un unico sitio para que menu, etiquetas y paginas de
// categoria no diverjan.
export interface DefinicionCategoria {
  clave: CategoriaArticulo;
  etiqueta: string;
  rutaSlug: string;
  descripcion: string;
}

export const CATEGORIAS: readonly DefinicionCategoria[] = [
  {
    clave: 'HARDWARE_PC',
    etiqueta: 'Hardware PC',
    rutaSlug: 'hardware-pc',
    descripcion: 'Procesadores, tarjetas gráficas, placas base, memoria, almacenamiento y monitores.',
  },
  {
    clave: 'MOVILES',
    etiqueta: 'Móviles',
    rutaSlug: 'moviles',
    descripcion: 'Smartphones, tablets y los procesadores que los mueven: lanzamientos, fichas técnicas y filtraciones.',
  },
  {
    clave: 'PERIFERICOS',
    etiqueta: 'Periféricos',
    rutaSlug: 'perifericos',
    descripcion: 'Teclados, ratones, auriculares, mandos y webcams.',
  },
  {
    clave: 'RENDIMIENTO',
    etiqueta: 'Rendimiento',
    rutaSlug: 'rendimiento',
    descripcion: 'Benchmarks, comparativas, consumo, temperaturas y drivers con efecto medible.',
  },
];

// Los articulos anteriores a la Fase 6 no tienen categoria: se muestran con
// esta etiqueta neutra en vez de dejar la tarjeta sin etiqueta.
export const ETIQUETA_SIN_CATEGORIA = 'Actualidad';

export function buscarCategoria(clave: CategoriaArticulo | null): DefinicionCategoria | undefined {
  return CATEGORIAS.find((categoria) => categoria.clave === clave);
}

export function obtenerEtiquetaCategoria(clave: CategoriaArticulo | null): string {
  return buscarCategoria(clave)?.etiqueta ?? ETIQUETA_SIN_CATEGORIA;
}

export function filtrarPorCategoria(articulos: ArticuloResumen[], clave: CategoriaArticulo): ArticuloResumen[] {
  return articulos.filter((articulo) => articulo.categoria === clave);
}
