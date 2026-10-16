/// <reference types="astro/client" />

interface ImportMetaEnv {
  readonly URL_API_BACKEND: string;
  // Vercel la define como "1" en sus builds; en local no existe.
  readonly VERCEL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
