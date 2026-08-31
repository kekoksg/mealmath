import {
  chaveDe,
  dataCurta,
  dataDe,
  dataPorExtenso,
  diaDaSemanaCurto,
  diaDoMes,
  diaEMes,
  gradeDoMes,
  inicioDaSemana,
  inicioDoMes,
  mesPorExtenso,
  somarDias,
  somarMeses,
} from './data';

describe('Datas do diário', () => {
  it('lê e escreve a data pelos campos locais, não pelos UTC', () => {
    // Esse teste passa em qualquer fuso, que e justamente o que o toISOString nao garante.
    expect(chaveDe(new Date(2026, 7, 3))).toBe('2026-08-03');

    const lida = dataDe('2026-08-03');
    expect([lida.getFullYear(), lida.getMonth(), lida.getDate()]).toEqual([2026, 7, 3]);
  });

  it('mantém o dia ao ir e voltar da string', () => {
    // No fuso do Brasil, new Date('2026-08-03') cairia no dia 2.
    expect(chaveDe(dataDe('2026-08-03'))).toBe('2026-08-03');
    expect(chaveDe(dataDe('2026-01-01'))).toBe('2026-01-01');
    expect(chaveDe(dataDe('2026-12-31'))).toBe('2026-12-31');
  });

  it('soma dias atravessando mês, ano e 29 de fevereiro', () => {
    expect(somarDias('2026-08-31', 1)).toBe('2026-09-01');
    expect(somarDias('2026-01-01', -1)).toBe('2025-12-31');
    expect(somarDias('2026-08-03', 7)).toBe('2026-08-10');
    expect(somarDias('2028-02-28', 1)).toBe('2028-02-29');
    expect(somarDias('2026-02-28', 1)).toBe('2026-03-01');
  });

  it('começa a semana no domingo', () => {
    // 2026-08-02 e um domingo, entao ele mesmo e o inicio da semana.
    expect(inicioDaSemana('2026-08-02')).toBe('2026-08-02');
    expect(inicioDaSemana('2026-08-06')).toBe('2026-08-02');

    // Sabado fecha a semana; o domingo seguinte ja abre a proxima.
    expect(inicioDaSemana('2026-08-08')).toBe('2026-08-02');
    expect(inicioDaSemana('2026-08-09')).toBe('2026-08-09');
  });

  it('rotula a coluna do dia', () => {
    expect(diaDaSemanaCurto('2026-08-03')).toBe('Seg');
    expect(diaDaSemanaCurto('2026-08-09')).toBe('Dom');
    expect(diaDoMes('2026-08-03')).toBe(3);
  });

  it('formata as datas exibidas em pt-BR', () => {
    expect(diaEMes('2026-08-01')).toBe('01 ago');
    expect(diaEMes('2026-12-25')).toBe('25 dez');
    expect(dataCurta('2026-08-03')).toBe('03/08/2026');
    expect(dataPorExtenso('2026-08-03')).toBe('segunda-feira, 3 de agosto de 2026');
  });

  it('ancora no dia 1 do mês', () => {
    expect(inicioDoMes('2026-08-17')).toBe('2026-08-01');
    expect(inicioDoMes('2026-08-01')).toBe('2026-08-01');
  });

  it('soma meses ao dia 1, sem pular mês curto', () => {
    // A base e sempre o dia 1, por isso 31/01 nunca aparece aqui.
    expect(somarMeses('2026-01-01', 1)).toBe('2026-02-01');
    expect(somarMeses('2026-12-01', 1)).toBe('2027-01-01');
    expect(somarMeses('2026-03-01', -1)).toBe('2026-02-01');
    expect(somarMeses('2026-01-01', -1)).toBe('2025-12-01');
  });

  it('nomeia o mês por extenso para o cabeçalho do calendário', () => {
    expect(mesPorExtenso('2026-08-17')).toBe('Agosto de 2026');
    expect(mesPorExtenso('2026-01-01')).toBe('Janeiro de 2026');
  });

  it('monta a grade do mês com 6 semanas começando no domingo', () => {
    const grade = gradeDoMes('2026-08-17');

    expect(grade.length).toBe(42);
    // 2026-08-01 e um sabado, entao a grade volta ate o domingo anterior (26/07).
    expect(grade[0].chave).toBe('2026-07-26');
    expect(grade[0].noMes).toBe(false);

    // Sabado e a setima coluna: e o que faz cada data cair sob a letra certa do cabecalho
    // (D S T Q Q S S). Com a semana comecando na segunda, 01/08 caia na sexta.
    expect(grade[6].chave).toBe('2026-08-01');
    expect(grade[6].noMes).toBe(true);

    const diasDoMes = grade.filter((dia) => dia.noMes);
    expect(diasDoMes.length).toBe(31);
    expect(diasDoMes[0].chave).toBe('2026-08-01');
    expect(diasDoMes[diasDoMes.length - 1].chave).toBe('2026-08-31');
  });

  it('grade do mês concorda com o início de semana usado na faixa de dias do diário', () => {
    const grade = gradeDoMes('2026-08-17');
    expect(grade[0].chave).toBe(inicioDaSemana(inicioDoMes('2026-08-17')));
  });
});
