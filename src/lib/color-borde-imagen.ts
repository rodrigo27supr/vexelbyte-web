import sharp from 'sharp';

// Colores del borde de una imagen, para rellenar el hueco del marco cuando la
// imagen no tiene su misma forma. Un solo color dejaba costura en las fotos
// cuyo fondo cambia de arriba abajo (un telon negro que acaba en azul), y
// mezclar los dos lados tambien: saco varias paradas de cada lado por separado
// y cada banda se rellena con el degradado de su propio borde. Se calcula en
// el build, nunca en el navegador del lector.

const LADO_MUESTRA = 24;
const PARADAS_DEGRADADO = 6;
const COLOR_POR_DEFECTO = '#ffffff';

// Paradas de cada borde: las laterales de arriba abajo, las de arriba y abajo de izquierda a derecha.
export interface RellenoBordeImagen {
  izquierda: string[];
  derecha: string[];
  arriba: string[];
  abajo: string[];
}

const RELLENO_POR_DEFECTO: RellenoBordeImagen = {
  izquierda: [COLOR_POR_DEFECTO],
  derecha: [COLOR_POR_DEFECTO],
  arriba: [COLOR_POR_DEFECTO],
  abajo: [COLOR_POR_DEFECTO],
};

// Varias tarjetas y la cabecera piden la misma foto: la calculo una vez por build.
const rellenosCalculados = new Map<string, Promise<RellenoBordeImagen>>();

export function obtenerRellenoBordeImagen(urlImagen: string): Promise<RellenoBordeImagen> {
  let rellenoPendiente = rellenosCalculados.get(urlImagen);
  if (!rellenoPendiente) {
    rellenoPendiente = calcularRellenoBorde(urlImagen);
    rellenosCalculados.set(urlImagen, rellenoPendiente);
  }
  return rellenoPendiente;
}

async function calcularRellenoBorde(urlImagen: string): Promise<RellenoBordeImagen> {
  try {
    const respuesta = await fetch(urlImagen);
    if (!respuesta.ok) {
      return RELLENO_POR_DEFECTO;
    }
    // flatten sobre blanco: una zona transparente de un PNG se ve blanca en la web.
    const { data: pixeles } = await sharp(Buffer.from(await respuesta.arrayBuffer()))
      .flatten({ background: COLOR_POR_DEFECTO })
      .resize(LADO_MUESTRA, LADO_MUESTRA, { fit: 'fill' })
      .removeAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
    const ultimaPosicion = LADO_MUESTRA - 1;
    return {
      izquierda: calcularParadas((posicion) => leerPixel(pixeles, posicion, 0)),
      derecha: calcularParadas((posicion) => leerPixel(pixeles, posicion, ultimaPosicion)),
      arriba: calcularParadas((posicion) => leerPixel(pixeles, 0, posicion)),
      abajo: calcularParadas((posicion) => leerPixel(pixeles, ultimaPosicion, posicion)),
    };
  } catch (errorImagen) {
    // Un fallo aqui solo cambia el color del relleno: nunca debe tumbar el build.
    console.warn(`No pude calcular el color de borde de ${urlImagen}`, errorImagen);
    return RELLENO_POR_DEFECTO;
  }
}

function leerPixel(pixeles: Buffer, fila: number, columna: number): number[] {
  const desplazamiento = (fila * LADO_MUESTRA + columna) * 3;
  return [pixeles[desplazamiento], pixeles[desplazamiento + 1], pixeles[desplazamiento + 2]];
}

// Recorro el lado en tramos iguales y me quedo con el color dominante de cada uno.
function calcularParadas(leerPosicion: (posicion: number) => number[]): string[] {
  const posicionesPorTramo = LADO_MUESTRA / PARADAS_DEGRADADO;
  return Array.from({ length: PARADAS_DEGRADADO }, (_tramoVacio, tramo) => {
    const coloresTramo: number[][] = [];
    for (let posicion = tramo * posicionesPorTramo; posicion < (tramo + 1) * posicionesPorTramo; posicion++) {
      coloresTramo.push(leerPosicion(posicion));
    }
    return calcularColorDominante(coloresTramo);
  });
}

// Color dominante, no la media: con la media, el producto que toca el borde
// agrisaba el blanco del fondo y se notaba la costura con la foto. Agrupo los
// pixeles en tonos parecidos y promedio solo el grupo mas numeroso.
const PASO_AGRUPACION = 24;

function calcularColorDominante(colores: number[][]): string {
  const gruposPorTono = new Map<string, number[][]>();
  for (const canales of colores) {
    const clave = canales.map((canal) => Math.floor(canal / PASO_AGRUPACION)).join('-');
    gruposPorTono.set(clave, [...(gruposPorTono.get(clave) ?? []), canales]);
  }
  const grupoDominante = [...gruposPorTono.values()].reduce((mayor, grupo) => (grupo.length > mayor.length ? grupo : mayor));
  return `#${[0, 1, 2]
    .map((canal) => grupoDominante.reduce((suma, colorPixel) => suma + colorPixel[canal], 0) / grupoDominante.length)
    .map((media) => Math.round(media).toString(16).padStart(2, '0'))
    .join('')}`;
}
