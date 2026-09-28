// Esquema JSON-LD basico compartido por las paginas de listado (portada,
// catalogo de hardware) que no tienen un tipo de Schema Markup propio como
// NewsArticle/Review/Product -- sin esto, Google no tiene forma de asociar
// el sitio con la organizacion VexelByte ni de resolver el logo en
// resultados de busqueda. Un solo helper porque las dos paginas que lo usan
// necesitan exactamente el mismo objeto, solo cambia la URL absoluta segun
// donde se invoque.
export function construirEsquemaOrganizacion(sitioAstro: URL | undefined) {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'VexelByte',
    url: sitioAstro?.toString(),
    publisher: {
      '@type': 'Organization',
      name: 'VexelByte',
      logo: { '@type': 'ImageObject', url: new URL('/favicon.svg', sitioAstro).toString() },
    },
  };
}
