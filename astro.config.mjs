// @ts-check
import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';
import sitemap from '@astrojs/sitemap';
import { loadEnv } from 'vite';

// Las fotos las sirve nuestra API: Astro solo puede optimizar imagenes de ese
// origen, el mismo que ya consulta en el build.
const { URL_API_BACKEND } = loadEnv(process.env.NODE_ENV ?? 'production', process.cwd(), '');
const urlApiBackend = new URL(URL_API_BACKEND || 'http://localhost:8080');

// https://astro.build/config
export default defineConfig({
  // Canonico con www porque es el dominio que sirve Vercel: el dominio raiz
  // redirige con 308 a www, y un canonical o un sitemap que apunten a una URL
  // que redirige confunden a Google. Sitemap, canonical y og:url salen de aqui.
  site: 'https://www.vexelbyte.com',
  vite: {
    plugins: [tailwindcss()],
    build: {
      // Sin esto Astro incrusta los scripts pequenos (el del banner de cookies)
      // como <script> inline, que la CSP de vercel.json bloquea: los quiero
      // siempre como archivo propio servido desde el mismo origen.
      assetsInlineLimit: 0,
    },
  },
  build: {
    // Astro incrusta en un <style> las hojas pequenas de cada componente, y la
    // CSP (style-src 'self') las bloquearia: siempre como archivo externo.
    inlineStylesheets: 'never',
  },
  image: {
    // Las fotos se descargan de nuestra API y se optimizan en el build, y se
    // sirven desde el dominio del sitio: la CSP (img-src 'self') no cambia y el
    // navegador del lector nunca contacta con el backend ni con Wikimedia.
    remotePatterns: [{ protocol: urlApiBackend.protocol.replace(':', ''), hostname: urlApiBackend.hostname }],
  },
  // El buscador lleva noindex: una pagina de resultados vacia en el HTML
  // estatico no aporta nada a Google y no debe estar en el sitemap.
  integrations: [sitemap({ filter: (urlPagina) => !urlPagina.includes('/buscar/') })],
});
