// BreadcrumbList para Google: con el, el resultado muestra la ruta
// "VexelByte > Moviles" en vez de la URL cruda, y refuerza la estructura por
// secciones. Coincide con las migas visibles de la pagina, como exige Google.
export interface MigaDePan {
  nombre: string;
  ruta: string;
}

export function construirEsquemaMigas(sitioAstro: URL | undefined, migas: MigaDePan[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: migas.map((miga, posicionMiga) => ({
      '@type': 'ListItem',
      position: posicionMiga + 1,
      name: miga.nombre,
      item: new URL(miga.ruta, sitioAstro).toString(),
    })),
  };
}
