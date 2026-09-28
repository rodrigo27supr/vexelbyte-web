import type { APIRoute } from 'astro';
import { getImage } from 'astro:assets';
import { obtenerArticulosPublicados } from '../../lib/api-articulos';
import { obtenerEtiquetaCategoria } from '../../lib/categorias';

// Indice del buscador, generado en el build como un JSON estatico mas: solo
// titular, entradilla, producto y seccion (no el cuerpo), que es lo que se
// busca de verdad y mantiene el archivo pequeno aunque el archivo crezca.
// La pagina /buscar/ lo descarga solo cuando alguien busca algo.
const ANCHO_MINIATURA = 240;

export const GET: APIRoute = async () => {
  const articulos = await obtenerArticulosPublicados();
  const entradasIndice = await Promise.all(
    articulos.map(async (articulo) => {
      const miniatura = articulo.foto
        ? await getImage({
            src: articulo.foto.url,
            width: ANCHO_MINIATURA,
            height: Math.round((ANCHO_MINIATURA * articulo.foto.alto) / articulo.foto.ancho),
            format: 'webp',
          })
        : null;
      return {
        slug: articulo.slug,
        titulo: articulo.titulo,
        descripcion: articulo.descripcion,
        producto: articulo.producto,
        seccion: obtenerEtiquetaCategoria(articulo.categoria),
        fechaPublicacion: articulo.fechaPublicacion,
        miniatura: miniatura?.src ?? null,
      };
    }),
  );
  return new Response(JSON.stringify(entradasIndice), { headers: { 'Content-Type': 'application/json' } });
};
