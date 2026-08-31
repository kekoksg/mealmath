/**
 * Datas do diario no formato yyyy-MM-dd, sempre no fuso local.
 *
 * O LocalDate do backend e so o dia, sem hora e sem fuso. Por isso nada aqui usa
 * toISOString() nem new Date('2026-08-03'): os dois tratam a data como UTC e, no Brasil,
 * acabam devolvendo o dia anterior. O almoco de segunda viraria registro de domingo so
 * porque o navegador esta em Sao Paulo.
 */

/** Data no formato yyyy-MM-dd, o mesmo que a API usa. */
export type DataIso = string;

/** O indice e o Date.getDay(). Sao os rotulos curtos da faixa de dias. */
const DIAS_CURTOS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'] as const;

const DIAS_LONGOS = [
  'domingo',
  'segunda-feira',
  'terça-feira',
  'quarta-feira',
  'quinta-feira',
  'sexta-feira',
  'sábado',
] as const;

const MESES_CURTOS = [
  'jan', 'fev', 'mar', 'abr', 'mai', 'jun',
  'jul', 'ago', 'set', 'out', 'nov', 'dez',
] as const;

const MESES_LONGOS = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
] as const;

function doisDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

/** Converte Date para yyyy-MM-dd lendo os campos locais, nunca os de UTC. */
export function chaveDe(data: Date): DataIso {
  return `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}`;
}

/**
 * Converte yyyy-MM-dd para um Date na meia-noite local.
 *
 * Quem garante isso e o construtor de 3 argumentos. Com new Date(chave), a string
 * seria lida como UTC e o dia mudaria na hora de exibir.
 */
export function dataDe(chave: DataIso): Date {
  const [ano, mes, dia] = chave.split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

export function hoje(): DataIso {
  return chaveDe(new Date());
}

/** Soma dias virando o mes e o ano sozinho, porque o setDate ja normaliza isso. */
export function somarDias(chave: DataIso, dias: number): DataIso {
  const data = dataDe(chave);
  data.setDate(data.getDate() + dias);
  return chaveDe(data);
}

/**
 * Devolve o domingo da semana da data, que e onde a faixa de dias comeca.
 *
 * A semana comeca no domingo, como no cabecalho do calendario (D S T Q Q S S) e na janela
 * SEMANA do dashboard. Quando isto devolvia segunda, a grade do mes saia deslocada em um dia
 * em relacao ao proprio cabecalho: cada data aparecia sob a letra do dia anterior.
 */
export function inicioDaSemana(chave: DataIso): DataIso {
  // getDay() ja devolve 0 no domingo, entao o deslocamento e ele mesmo.
  return somarDias(chave, -dataDe(chave).getDay());
}

/** Dia 1 do mes da data. E a base do calendario e nunca estoura para o mes seguinte. */
export function inicioDoMes(chave: DataIso): DataIso {
  const data = dataDe(chave);
  return chaveDe(new Date(data.getFullYear(), data.getMonth(), 1));
}

/**
 * Soma meses ao dia 1 de um mes.
 *
 * So e seguro chamar passando uma data que ja e dia 1. Somar um mes a 31/01 pularia
 * fevereiro, porque o setMonth normaliza para 03/03. Como o calendario sempre navega a
 * partir do inicioDoMes, esse caso nao acontece aqui.
 */
export function somarMeses(chave: DataIso, meses: number): DataIso {
  const data = dataDe(chave);
  data.setMonth(data.getMonth() + meses);
  return chaveDe(data);
}

/** Monta "Agosto de 2026" para o cabecalho do calendario. */
export function mesPorExtenso(chave: DataIso): string {
  const data = dataDe(chave);
  const mes = MESES_LONGOS[data.getMonth()];
  return `${mes[0].toUpperCase()}${mes.slice(1)} de ${data.getFullYear()}`;
}

/** Uma celula da grade do calendario. */
export interface DiaDoCalendario {
  readonly chave: DataIso;
  /** Numero do dia no mes, que e o que aparece dentro da celula. */
  readonly numero: number;
  /** Fica false nas celulas que so completam a grade, do mes anterior ou do seguinte. */
  readonly noMes: boolean;
}

/**
 * Grade de 6 semanas (42 dias) do mes da data, comecando no domingo anterior ao dia 1.
 *
 * Mesma regra do inicioDaSemana, para o calendario e a faixa de dias nunca discordarem de onde a
 * semana comeca. Fixa em 6 semanas para a grade nao mudar de altura de um mes para o outro.
 */
export function gradeDoMes(chave: DataIso): DiaDoCalendario[] {
  const mesAlvo = dataDe(inicioDoMes(chave)).getMonth();
  const inicio = inicioDaSemana(inicioDoMes(chave));

  return Array.from({ length: 42 }, (_, deslocamento) => {
    const dia = somarDias(inicio, deslocamento);
    return {
      chave: dia,
      numero: diaDoMes(dia),
      noMes: dataDe(dia).getMonth() === mesAlvo,
    };
  });
}

/** "Seg", "Ter": o rotulo de cima na coluna do dia. */
export function diaDaSemanaCurto(chave: DataIso): string {
  return DIAS_CURTOS[dataDe(chave).getDay()];
}

/** E o numero grande que aparece na coluna do dia. */
export function diaDoMes(chave: DataIso): number {
  return dataDe(chave).getDate();
}

/** "01 ago", usado nas duas pontas do intervalo da semana. */
export function diaEMes(chave: DataIso): string {
  const data = dataDe(chave);
  return `${doisDigitos(data.getDate())} ${MESES_CURTOS[data.getMonth()]}`;
}

/** "03/08/2026", para a data aparecer no meio de uma frase. */
export function dataCurta(chave: DataIso): string {
  const data = dataDe(chave);
  return `${doisDigitos(data.getDate())}/${doisDigitos(data.getMonth() + 1)}/${data.getFullYear()}`;
}

/**
 * Monta "Janeiro de 2026" a partir do criadoEm, que e o unico valor com hora e fuso que chega aqui.
 * Converter para o fuso local e o certo neste caso: o mes em que a pessoa abriu a conta e o do
 * relogio dela.
 *
 * Devolve null quando nao da para ler, porque token antigo pode nao ter o campo. Ai a linha some da
 * tela em vez de mostrar algo errado.
 */
export function mesEAnoDe(instante: string | null | undefined): string | null {
  if (!instante) {
    return null;
  }

  const data = new Date(instante);
  if (Number.isNaN(data.getTime())) {
    return null;
  }

  const mes = MESES_LONGOS[data.getMonth()];
  return `${mes[0].toUpperCase()}${mes.slice(1)} de ${data.getFullYear()}`;
}

/** "segunda-feira, 3 de agosto de 2026", para o leitor de tela e frases mais longas. */
export function dataPorExtenso(chave: DataIso): string {
  const data = dataDe(chave);
  const semana = DIAS_LONGOS[data.getDay()];
  return `${semana}, ${data.getDate()} de ${MESES_LONGOS[data.getMonth()]} de ${data.getFullYear()}`;
}
