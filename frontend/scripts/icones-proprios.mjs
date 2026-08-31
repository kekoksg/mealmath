/**
 * Ícones de comida desenhados para o MealMath.
 *
 * <h2>Por que existem</h2>
 * O Solar Icon Set cobre bem interface, mas não comida: não tem carne, arroz, trigo, ovo,
 * sanduíche, macarrão nem banana. Ficar só com o que ele oferece obrigava a trocar o prato
 * pelo conceito ("almoço" virava um prato genérico, "proteína" virava um halter). Estes
 * aqui devolvem o vocabulário que o app precisa.
 *
 * <h2>Como desenhar mais</h2>
 * Para conviverem com os do Solar sem destoar, todo desenho segue as mesmas convenções:
 *
 * - Caixa 24×24, sem `fill` (o traço é quem desenha).
 * - `stroke-width: 1.5`, pontas e junções arredondadas.
 * - `stroke="currentColor"` — a cor vem de quem usa o ícone.
 * - Formas simples e poucas linhas: o ícone precisa ler a 20px no menu.
 *
 * O arquivo é escrito à mão de propósito e o gerador só o mistura ao mapa final, então
 * `node scripts/gerar-icones.mjs` nunca sobrescreve o que está aqui.
 */

/** Envelope comum: só o miolo muda de ícone para ícone. */
const traco = (miolo) =>
  `<g fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${miolo}</g>`;

export const PROPRIOS = {
  // ---------- Categorias de alimento ----------

  /** Corte de carne: contorno irregular e o osso no canto. */
  'cat-proteina': traco(
      '<path d="M4.9 9.6c1.6-3.9 6.1-5.3 9.7-4.5 3.4.8 5.4 3.4 5 6.3-.4 2.9-3 4.6-5.5 5.4-3 1-6.6 1.2-8.4-.9-1.4-1.6-1.5-4.2-.8-6.3Z"/>'
      + '<ellipse cx="8.4" cy="11.9" rx="2" ry="1.6"/>'
  ),

  /** Cenoura: raiz e folhas. Lê à primeira vista, o que brócolis a 20px não fazia. */
  'cat-hortifruti': traco(
      '<path d="m7.9 9.2 4.1 11.2 4.1-11.2z"/>'
      + '<path d="M12 9.2V6.1"/>'
      + '<path d="M12 6.9c-1-1.3-2.8-1.4-2.8-1.4s.1 1.7 1.4 2.5M12 6.9c1-1.3 2.8-1.4 2.8-1.4s-.1 1.7-1.4 2.5"/>'
  ),

  /** Pão de forma: um pão com a casca marcada. */
  'cat-carboidrato': traco(
    '<path d="M4.5 11.5h15V17a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2z"/>' +
      '<path d="M4.5 11.5a3.5 3.5 0 0 1 3.5-3.5h8a3.5 3.5 0 0 1 3.5 3.5"/>'
  ),

  /** Copo de leite com o nível marcado. */
  'cat-laticinio': traco(
    '<path d="M7.5 4h9l-1 15.2a1.8 1.8 0 0 1-1.8 1.7h-3.4a1.8 1.8 0 0 1-1.8-1.7z"/>' +
      '<path d="M7.9 10h8.2"/>'
  ),

  // ---------- Ícones de refeição ----------

  /** Ovo frito: clara e gema fora do centro. */
  'ref-manha': traco(
      '<ellipse cx="12" cy="12.4" rx="8" ry="6.6"/>'
      + '<circle cx="9.2" cy="10.4" r="2.4"/>'
  ),

  /** Xícara de café com vapor. */
  'ref-cafe': traco(
    '<path d="M5 10.5h10.5V15a4.5 4.5 0 0 1-4.5 4.5h-1.5A4.5 4.5 0 0 1 5 15z"/>' +
      '<path d="M15.5 12h1.3a2.6 2.6 0 0 1 0 5.2h-.5"/>' +
      '<path d="M8.5 7.5c0-1 1-1.3 1-2.3M12 7.5c0-1 1-1.3 1-2.3"/>'
  ),

  /** Prato com garfo e faca — a refeição principal. */
  'ref-almoco': traco(
    '<circle cx="12" cy="12" r="4.4"/>' +
      '<path d="M3.6 3.5v3.9a1.9 1.9 0 0 0 3.8 0V3.5"/>' +
      '<path d="M5.5 9.3V20.5"/>' +
      '<path d="M19.4 3.5c1.2 1.9 1.2 6.4 0 8.3v8.7"/>'
  ),

  /** Travessa com cúpula e pegador: o jantar servido. */
  'ref-jantar': traco(
      '<path d="M2.6 19.4h18.8"/>'
      + '<path d="M4.4 19.4a7.6 7.6 0 0 1 15.2 0"/>'
      + '<path d="M12 11.8V10"/>'
      + '<circle cx="12" cy="8.6" r="1.5"/>'
  ),

  /** Tigela com folhas saindo pela borda. */
  'ref-salada': traco(
      '<path d="M3.5 12.4h17a8.5 8.5 0 0 1-17 0Z"/>'
      + '<path d="M11.4 12.4c-2.4-.5-3.8-2.6-3.3-4.8 2.3-.2 4.2 1.5 4.2 3.7"/>'
      + '<path d="M13 12.4c2-.9 2.9-3 2-4.9-2 .4-3.3 2.3-3 4.3"/>'
  ),

  /** Maçã com folha. */
  'ref-fruta': traco(
    '<path d="M12 8.4c-1-1-3.6-1.7-5.2 0-2 2.1-1.4 6.6.9 9.3 1.3 1.5 2.9 1.6 4.3.8 1.4.8 3 .7 4.3-.8 2.3-2.7 2.9-7.2.9-9.3-1.6-1.7-4.2-1-5.2 0Z"/>' +
      '<path d="M12 8.4V5.8"/>' +
      '<path d="M12 6c1.4-1.6 3.2-1.5 3.2-1.5s.2 1.8-1.3 2.6"/>'
  ),

  /** Tigela com colher: cereal, sopa, mingau. */
  'ref-cereal': traco(
    '<path d="M3.5 11.5h17a8.5 8.5 0 0 1-17 0Z"/>' +
      '<path d="m14.5 8.4 4.6-4.6"/>' +
      '<path d="M17.3 3.3a1.9 1.9 0 0 1 2.7 2.7l-1 1-2.7-2.7Z"/>'
  ),

  /** Sanduíche de lado: pão de cima, recheio e pão de baixo. */
  'ref-sanduiche': traco(
      '<path d="M4.5 11.4a7.5 7.5 0 0 1 15 0z"/>'
      + '<path d="M4.5 14.2c1.9-1.3 3.2 1.3 5 0s3.2 1.3 5 0 3.1 1.3 5 0"/>'
      + '<path d="M4.5 17h15a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2Z"/>'
  ),

  /** Espaguete enrolado no garfo — sem tigela, para não repetir salada e cereal. */
  'ref-massa': traco(
      '<path d="M8.6 20.5V12"/>'
      + '<path d="M5.6 3.5v4.3a3 3 0 0 0 6 0V3.5"/>'
      + '<path d="M5.6 3.5v3.4M8.6 3.5v3.4M11.6 3.5v3.4"/>'
      + '<path d="M13.4 11c1.6-1.4 4.4-1 5.5 1s.2 4.8-2.1 5.4c-2 .5-4.1-.9-4.3-3"/>'
      + '<path d="M14.2 13.6c.8-.8 2.2-.6 2.7.5s-.2 2.3-1.4 2.4"/>'
  ),

  /** Panela com tampa: a comida feita em casa. */
  'ref-arroz': traco(
      '<path d="M4.4 10.5h15.2v5.8a3.2 3.2 0 0 1-3.2 3.2H7.6a3.2 3.2 0 0 1-3.2-3.2z"/>'
      + '<path d="M2.6 10.5h18.8"/>'
      + '<path d="M12 10.5V7.9"/>'
      + '<circle cx="12" cy="6.7" r="1.2"/>'
  ),

  /** Cupcake: forminha, cobertura e cereja. */
  'ref-doce': traco(
      '<path d="M6.6 12.6h10.8l-1.1 6.6a1.8 1.8 0 0 1-1.8 1.5H9.5a1.8 1.8 0 0 1-1.8-1.5z"/>'
      + '<path d="M6.6 12.6a5.4 5.4 0 0 1 10.8 0z"/>'
      + '<path d="M12 7.2V5.8"/>'
      + '<circle cx="12" cy="4.4" r="1.3"/>'
  ),

  /** Copo com canudo. */
  'ref-bebida': traco(
    '<path d="M6.5 7.5h11l-1.2 12a2 2 0 0 1-2 1.9h-4.6a2 2 0 0 1-2-1.9z"/>' +
      '<path d="M6.9 11.5h10.2"/>' +
      '<path d="m13.5 7.5 2.8-4.5"/>'
  ),

  /** Marmita: a caixa com a divisória e o fecho. */
  'ref-marmita': traco(
    '<rect x="3.5" y="8.5" width="17" height="11" rx="2.2"/>' +
      '<path d="M3.5 12.5h17"/>' +
      '<path d="M8.6 8.5V6.3a1.8 1.8 0 0 1 1.8-1.8h3.2a1.8 1.8 0 0 1 1.8 1.8v2.2"/>'
  ),
};
