import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Dashboard } from './dashboard';
import { DashboardResponse } from './dashboard.model';

const URL = `${environment.apiUrl}/dashboard`;
const URL_META = `${environment.apiUrl}/meta-orcamento`;

/** Semana com custo, meta definida e composicao. E o caso completo da tela. */
const SEMANA: DashboardResponse = {
  periodo: 'SEMANA',
  inicio: '2026-07-29',
  fim: '2026-08-04',
  custoTotal: 187.45,
  custoMedioPorDia: 26.778571428,
  comparativo: {
    inicio: '2026-07-22',
    fim: '2026-07-28',
    custoTotal: 165.2,
    variacaoPercentual: 13.47,
  },
  completude: { diasComRegistro: 5, totalDeDias: 7 },
  meta: {
    valor: 450,
    periodo: 'MENSAL',
    valorNoPeriodo: 105,
    percentualConsumido: 178.52,
    saldo: -82.45,
    acimaDaMeta: true,
  },
  composicaoPorCategoria: [
    { categoria: 'PROTEINA', rotulo: 'Proteína', custo: 96.3, percentual: 51.37 },
    { categoria: 'CARBOIDRATO', rotulo: 'Carboidrato', custo: 55.15, percentual: 29.42 },
    { categoria: 'HORTIFRUTI', rotulo: 'Hortifruti', custo: 36.0, percentual: 19.21 },
  ],
  itensMaiorImpacto: [
    {
      itemMercadoId: 7,
      nome: 'Peito de frango',
      categoria: 'PROTEINA',
      custo: 62.4,
      percentual: 33.29,
    },
    { itemMercadoId: 3, nome: 'Arroz', categoria: 'CARBOIDRATO', custo: 21.1, percentual: 11.26 },
  ],
  altasDePreco: [],
  itensSemPreco: [],
};

function painel(ajustes: Partial<DashboardResponse> = {}): DashboardResponse {
  return { ...SEMANA, ...ajustes };
}

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texto(seletor: string): string | undefined {
    return tela().querySelector(seletor)?.textContent?.replace(/\s+/g, ' ').trim();
  }

  /** Responde a carga pendente e confere se o periodo pedido foi o certo. */
  function consolidar(dados: DashboardResponse, periodo = 'SEMANA'): void {
    const pedido = http.expectOne((requisicao) => requisicao.url === URL);
    expect(pedido.request.params.get('periodo')).toBe(periodo);
    pedido.flush(dados);
    fixture.detectChanges();
  }

  /**
   * A tela abre em "Hoje", mas a maioria dos testes olha a janela da semana.
   * Entao responde a carga inicial do dia e troca para o botao Semana.
   */
  function abrirNaSemana(dados: DashboardResponse = SEMANA): void {
    consolidar(painel({ periodo: 'DIA' }), 'DIA');
    tela().querySelectorAll<HTMLButtonElement>('.pill')[1].click();
    fixture.detectChanges();
    consolidar(dados);
  }

  it('abre em Hoje, sem exigir escolha de período', () => {
    consolidar(painel({ periodo: 'DIA', custoTotal: 12.4 }), 'DIA');

    const pilulas = tela().querySelectorAll<HTMLButtonElement>('.pill');
    expect(pilulas[0].textContent?.trim()).toBe('Hoje');
    expect(pilulas[0].getAttribute('aria-pressed')).toBe('true');
    expect(pilulas[1].getAttribute('aria-pressed')).toBe('false');
    expect(texto('.hero .lbl')).toBe('Custo total — Hoje');
    expect(texto('.hero .val')).toBe('R$ 12,40');
  });

  it('mostra total, média por dia e completude da janela', () => {
    abrirNaSemana();

    expect(texto('.hero .lbl')).toBe('Custo total — Semana');
    expect(texto('.hero .val')).toBe('R$ 187,45');
    // A media e sobre os 7 dias da janela e nao sobre os 5 que tem registro.
    expect(texto('.hero .foot .l')).toBe('R$ 26,78 /dia');
    expect(texto('.hero .foot .r')).toBe('Dias registrados: 5/7');
  });

  it('omite a completude no período de um dia, onde ela não informa nada', () => {
    consolidar(
      painel({
        periodo: 'DIA',
        custoMedioPorDia: 12.4,
        completude: { diasComRegistro: 1, totalDeDias: 1 },
      }),
      'DIA'
    );

    // A média continua; some só o "1/1", que não expõe lacuna nenhuma.
    expect(texto('.hero .foot .l')).toBe('R$ 12,40 /dia');
    expect(tela().querySelector('.hero .foot .r')).toBeNull();
  });

  it('mostra a variação contra o período anterior e a oculta sem base de comparação', () => {
    abrirNaSemana();
    expect(texto('.hero .chip')).toBe('▲ +13% vs. semana passada');

    // Sem custo no periodo anterior, a variacao nao e 0% nem +100%: ela nao existe.
    tela().querySelectorAll<HTMLButtonElement>('.pill')[0].click();
    fixture.detectChanges();
    consolidar(painel({ periodo: 'DIA', comparativo: null }), 'DIA');

    expect(tela().querySelector('.hero .chip')).toBeNull();
    expect(texto('.hero .lbl')).toBe('Custo total — Hoje');
  });

  it('marca a alta como alerta e deixa a queda neutra', () => {
    // Gastar mais que no periodo anterior e a informacao ruim num app de custo, entao a tag
    // sai do verde e assume a cor de alerta. A queda fica na tag neutra.
    abrirNaSemana();
    expect(tela().querySelector('.hero .chip')?.classList.contains('alta')).toBe(true);

    tela().querySelectorAll<HTMLButtonElement>('.pill')[2].click();
    fixture.detectChanges();
    consolidar(
      painel({
        periodo: 'MES',
        comparativo: {
          inicio: '2026-07-01',
          fim: '2026-07-31',
          custoTotal: 210,
          variacaoPercentual: -8.2,
        },
      }),
      'MES'
    );

    expect(texto('.hero .chip')).toBe('▼ -8% vs. mês passado');
    expect(tela().querySelector('.hero .chip')?.classList.contains('alta')).toBe(false);
  });

  it('trocar de período refaz a consolidação no servidor', () => {
    abrirNaSemana();

    tela().querySelectorAll<HTMLButtonElement>('.pill')[2].click();
    fixture.detectChanges();
    consolidar(painel({ periodo: 'MES', custoTotal: 742.9 }), 'MES');

    expect(texto('.hero .val')).toBe('R$ 742,90');

    // Clicar no botao que ja esta selecionado nao dispara requisicao nova.
    tela().querySelectorAll<HTMLButtonElement>('.pill')[2].click();
    fixture.detectChanges();
    http.expectNone((requisicao) => requisicao.url === URL);
  });

  it('barra da meta trava em 100% e o estouro vai para a legenda', () => {
    abrirNaSemana();

    const barra = tela().querySelector<HTMLElement>('.track span')!;
    // O percentualConsumido e 178,52. A barra acabou, mas o tamanho do estouro continua visivel.
    expect(barra.style.width).toBe('100%');
    expect(barra.className).toBe('acima');
    expect(texto('.prog-cap')).toBe('R$ 82,45 acima da meta');
    expect(texto('.prog-row .a')).toBe('R$ 187,45');
    expect(texto('.prog-row .b')).toBe('/ R$ 105,00');
    // O cartao tem so essas quatro linhas: titulo, valores, barra e saldo.
    expect(tela().querySelector('.prog-nota')).toBeNull();
    expect(texto('.card-t .link')).toBe('editar');
  });

  it('barra vira âmbar na zona de risco e verde dentro da meta', () => {
    abrirNaSemana(
      painel({
        custoTotal: 90,
        meta: {
          valor: 450,
          periodo: 'MENSAL',
          valorNoPeriodo: 105,
          percentualConsumido: 85.71,
          saldo: 15,
          acimaDaMeta: false,
        },
      })
    );
    expect(tela().querySelector('.track span')!.className).toBe('risco');
    // O sufixo e da janela que esta na tela. Com meta mensal vista por semana, o que sobra
    // e o saldo da semana.
    expect(texto('.prog-cap')).toBe('R$ 15,00 ainda disponível na semana');

    tela().querySelectorAll<HTMLButtonElement>('.pill')[0].click();
    fixture.detectChanges();
    consolidar(
      painel({
        periodo: 'DIA',
        custoTotal: 5,
        meta: {
          valor: 450,
          periodo: 'MENSAL',
          valorNoPeriodo: 15,
          percentualConsumido: 33.33,
          saldo: 10,
          acimaDaMeta: false,
        },
      }),
      'DIA'
    );
    expect(tela().querySelector('.track span')!.className).toBe('ok');
  });

  it('sem meta definida oferece defini-la e grava pelo formulário', () => {
    abrirNaSemana(painel({ meta: null }));

    expect(tela().querySelector('.track')).toBeNull();
    expect(texto('.card .btn-out')).toBe('Definir meta');

    tela().querySelector<HTMLButtonElement>('.card .btn-out')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();

    // Valor zerado nao passa. A validacao existe justamente para barrar antes de salvar.
    fixture.componentInstance['formMeta'].patchValue({ valor: 0 });
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone({ method: 'PUT', url: URL_META });
    expect(texto('.erro-campo')).toBe('O valor da meta deve ser maior que zero.');

    fixture.componentInstance['formMeta'].patchValue({ valor: 450, periodo: 'MENSAL' });
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const gravacao = http.expectOne({ method: 'PUT', url: URL_META });
    expect(gravacao.request.body).toEqual({ valor: 450, periodo: 'MENSAL' });
    gravacao.flush({
      id: 2,
      valor: 450,
      periodo: 'MENSAL',
      atualizadoEm: '2026-08-04T09:30:00Z',
    });
    fixture.detectChanges();

    // Recarrega, porque o rateio para a janela e conta do backend e nao do formulario.
    consolidar(SEMANA);
    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(tela().querySelector('.track')).not.toBeNull();
  });

  it('edição da meta abre preenchida com o valor cadastrado', () => {
    abrirNaSemana();

    tela().querySelector<HTMLButtonElement>('.card-t .link')!.click();
    fixture.detectChanges();

    expect(tela().querySelector<HTMLInputElement>('#meta-valor')!.value).toBe('450');
    expect(tela().querySelector<HTMLSelectElement>('#meta-periodo')!.value).toBe('MENSAL');
  });

  it('período sem registro mostra estado vazio apontando para o diário', () => {
    abrirNaSemana(
      painel({
        custoTotal: 0,
        custoMedioPorDia: 0,
        comparativo: null,
        completude: { diasComRegistro: 0, totalDeDias: 7 },
        composicaoPorCategoria: [],
        itensMaiorImpacto: [],
      })
    );

    expect(texto('.empty h4')).toBe('Sem registros no período');
    expect(tela().querySelector<HTMLAnchorElement>('.empty .btn-out')!.getAttribute('href')).toBe(
      '/diario'
    );
    // O topo da tela continua aparecendo: R$ 0,00 em 0 de 7 dias e informacao e nao tela vazia.
    expect(texto('.hero .val')).toBe('R$ 0,00');
    expect(tela().querySelector('.stack')).toBeNull();
  });

  it('total zerado por falta de preço manda para o mercado, não para o diário', () => {
    abrirNaSemana(
      painel({
        custoTotal: 0,
        custoMedioPorDia: 0,
        comparativo: null,
        composicaoPorCategoria: [],
        itensMaiorImpacto: [],
        itensSemPreco: ['Whey Protein'],
      })
    );

    expect(texto('.empty h4')).toBe('Nenhum custo apurado');
    expect(tela().querySelector<HTMLAnchorElement>('.empty .btn-out')!.getAttribute('href')).toBe(
      '/mercado'
    );
  });

  it('avisa sobre item sem preço que ficou fora do total', () => {
    abrirNaSemana();
    expect(tela().querySelector('.aviso-total')).toBeNull();

    tela().querySelectorAll<HTMLButtonElement>('.pill')[0].click();
    fixture.detectChanges();
    consolidar(painel({ periodo: 'DIA', itensSemPreco: ['Whey Protein', 'Azeite'] }), 'DIA');

    // Sem esse aviso o custo total estaria enganando o usuario.
    expect(texto('.aviso-total')).toBe(
      '2 itens ficaram fora do total por não ter preço: Whey Protein, Azeite.'
    );
  });

  it('desenha a composição e os itens de maior impacto', () => {
    abrirNaSemana();

    const fatias = tela().querySelectorAll<HTMLElement>('.stack span');
    expect(fatias.length).toBe(3);
    expect(fatias[0].style.width).toBe('51.37%');
    expect(fatias[0].dataset['catForte']).toBe('PROTEINA');

    const rotulos = Array.from(tela().querySelectorAll('.lg .n'), (n) => n.textContent?.trim());
    expect(rotulos).toEqual(['Proteína', 'Carboidrato', 'Hortifruti']);
    expect(texto('.lg .a')).toBe('R$ 96,30');
    expect(texto('.lg .p')).toBe('51%');

    expect(tela().querySelectorAll('.row').length).toBe(2);
    // O icone da categoria agora e um SVG desenhado direto no HTML. A cor de fundo continua
    // vindo do data-cat, que e o que liga a linha a categoria.
    const icone = tela().querySelector('.row app-icone.em')!;
    expect(icone.getAttribute('data-cat')).toBe('PROTEINA');
    expect(icone.querySelector('svg')).not.toBeNull();
    expect(texto('.row .t')).toBe('Peito de frango');
    expect(texto('.row .s')).toBe('33% do total');
    expect(texto('.row .v')).toBe('R$ 62,40');
  });

  it('alerta de preço mostra a etiqueta quando a embalagem não mudou', () => {
    abrirNaSemana(
      painel({
        altasDePreco: [
          {
            itemMercadoId: 7,
            nome: 'Peito de frango',
            precoAnterior: 16.5,
            precoAtual: 18.9,
            custoUnitarioAnterior: 0.0165,
            custoUnitarioAtual: 0.0189,
            unidadeBase: 'g',
            variacaoPercentual: 14.55,
            alteradoEm: '2026-07-30T11:12:00Z',
          },
        ],
      })
    );

    expect(texto('.alert .t')).toBe('Peito de frango');
    expect(texto('.alert .s')).toBe('R$ 16,50 → R$ 18,90');
    expect(texto('.alert .v')).toBe('+15%');
  });

  it('alerta mostra o custo unitário quando a embalagem mudou de tamanho', () => {
    abrirNaSemana(
      painel({
        altasDePreco: [
          {
            itemMercadoId: 9,
            nome: 'Leite',
            // O preco da etiqueta subiu 8%, mas a embalagem diminuiu, entao o custo por mL
            // subiu 46%.
            precoAnterior: 5.2,
            precoAtual: 5.6,
            custoUnitarioAnterior: 0.0052,
            custoUnitarioAtual: 0.00747,
            unidadeBase: 'mL',
            variacaoPercentual: 43.65,
            alteradoEm: '2026-07-30T11:12:00Z',
          },
        ],
      })
    );

    // A etiqueta iria contra o "+44%" do lado, porque a conta de verdade e no custo por mL.
    expect(texto('.alert .s')).toBe('R$ 0,52 → R$ 0,75 /100mL');
    expect(texto('.alert .v')).toBe('+44%');
  });

  it('alerta de preço aparece mesmo com o período vazio', () => {
    // Esse alerta responde "o que subiu desde a ultima compra" e nao filtra pela janela.
    abrirNaSemana(
      painel({
        custoTotal: 0,
        custoMedioPorDia: 0,
        comparativo: null,
        composicaoPorCategoria: [],
        itensMaiorImpacto: [],
        altasDePreco: [
          {
            itemMercadoId: 7,
            nome: 'Peito de frango',
            precoAnterior: 16.5,
            precoAtual: 18.9,
            custoUnitarioAnterior: 0.0165,
            custoUnitarioAtual: 0.0189,
            unidadeBase: 'g',
            variacaoPercentual: 14.55,
            alteradoEm: '2026-07-30T11:12:00Z',
          },
        ],
      })
    );

    expect(texto('.empty h4')).toBe('Sem registros no período');
    expect(tela().querySelectorAll('.alert').length).toBe(1);
  });

  it('oferece tentar de novo quando a consolidação falha', () => {
    http
      .expectOne((requisicao) => requisicao.url === URL)
      .error(new ProgressEvent('erro'), { status: 500 });
    fixture.detectChanges();

    expect(texto('.empty h4')).toBe('Não deu para carregar');

    tela().querySelector<HTMLButtonElement>('.empty .btn-out')!.click();
    fixture.detectChanges();
    consolidar(painel({ periodo: 'DIA' }), 'DIA');

    expect(texto('.hero .val')).toBe('R$ 187,45');
  });
});
