// Cliente HTTP minimo para la API de lectura del backend (ArticulosController).
// Solo se llama desde el frontmatter de paginas Astro o desde getStaticPaths,
// nunca desde el navegador: el sitio es 100% estatico y este fetch ocurre en
// build-time, asi que el HTML final ya trae el articulo completo y el LCP no
// depende de que el navegador del lector alcance nuestro backend.

const urlBaseApiBackend = import.meta.env.URL_API_BACKEND ?? 'http://localhost:8080';

// En el build de Vercel (define VERCEL=1) un fallo de la API tumba el build a
// proposito: Vercel conserva entonces el despliegue anterior en vez de
// publicar un sitio vacio porque Render estaba dormido o caido. En local sigo
// degradando con gracia para poder trabajar el frontend sin backend.
const esBuildDeProduccion = import.meta.env.VERCEL === '1';

function gestionarFalloApi(mensaje: string, causa?: unknown): void {
  if (esBuildDeProduccion) {
    throw new Error(mensaje, { cause: causa });
  }
  console.warn(mensaje, causa ?? '');
}

// Aislo el fallo de red del estado HTTP para que el throw de produccion no
// lo capture su propio catch. null significa que ni siquiera hubo respuesta.
async function pedirApi(ruta: string): Promise<Response | null> {
  try {
    return await fetch(`${urlBaseApiBackend}${ruta}`);
  } catch (errorConexion) {
    gestionarFalloApi(`No pude conectar con la API del backend en ${ruta} durante el build.`, errorConexion);
    return null;
  }
}

export type CategoriaArticulo = 'HARDWARE_PC' | 'MOVILES' | 'PERIFERICOS' | 'RENDIMIENTO';

export interface EspecificacionTecnica {
  nombre: string;
  valor: string;
}

// Foto del articulo con lo necesario para citarla: material de prensa del
// fabricante (autor es la marca) o foto ilustrativa de Pexels. Nula solo si
// aun no se ha revisado: entonces se usa la portada de datos. La sirve nuestra
// API (url absoluta tras resolverFoto), nunca un tercero.
export interface FotoArticulo {
  url: string;
  ancho: number;
  alto: number;
  autor: string;
  licencia: string;
  urlLicencia: string | null;
  urlOrigen: string;
  // Foto de banco de imagenes (Pexels) que ilustra el tema, no el producto.
  esIlustrativa: boolean;
}

export interface ArticuloResumen {
  slug: string;
  titulo: string;
  descripcion: string;
  fechaPublicacion: string;
  autor: string;
  // Nulos en los articulos anteriores a la Fase 6: el frontend usa una
  // categoria generica y el titular en la portada generada.
  categoria: CategoriaArticulo | null;
  producto: string | null;
  foto: FotoArticulo | null;
  // Hasta tres filas de la ficha que caben en una linea, para la portada de datos.
  datosClave: EspecificacionTecnica[];
}

export interface ArticuloDetalle extends ArticuloResumen {
  cuerpoHtml: string;
  fechaActualizacion: string | null;
  // Listas vacias (nunca nulas) cuando el articulo no las tiene.
  especificaciones: EspecificacionTecnica[];
  puntosClave: string[];
}

// La API da la ruta relativa de la foto; Astro la necesita absoluta para
// descargarla y optimizarla en el build.
function resolverFoto<Articulo extends ArticuloResumen>(articulo: Articulo): Articulo {
  if (articulo.foto) {
    articulo.foto.url = new URL(articulo.foto.url, urlBaseApiBackend).href;
  }
  return articulo;
}

export async function obtenerArticulosPublicados(): Promise<ArticuloResumen[]> {
  const respuesta = await pedirApi('/api/articulos');
  if (respuesta === null) {
    return [];
  }
  if (!respuesta.ok) {
    gestionarFalloApi(`La API de articulos respondio ${respuesta.status} durante el build.`);
    return [];
  }
  return ((await respuesta.json()) as ArticuloResumen[]).map(resolverFoto);
}

export async function obtenerArticuloPorSlug(slug: string): Promise<ArticuloDetalle | null> {
  const respuesta = await pedirApi(`/api/articulos/${slug}`);
  // Un 404 no es un fallo: el slug simplemente no existe o no esta publicado.
  if (respuesta === null || respuesta.status === 404) {
    return null;
  }
  if (!respuesta.ok) {
    gestionarFalloApi(`La API de articulos respondio ${respuesta.status} para el slug "${slug}".`);
    return null;
  }
  return resolverFoto((await respuesta.json()) as ArticuloDetalle);
}
