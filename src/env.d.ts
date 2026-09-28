/// <reference types="astro/client" />

interface ImportMetaEnv {
  readonly URL_API_BACKEND: string;
  // Vercel la define como "1" en sus builds; en local no existe.
  readonly VERCEL?: string;
  // Opcional: sin ella no se renderiza el banner de cookies ni se carga GA4.
  readonly ID_MEDICION_GA4?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
