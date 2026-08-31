import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { DataIso, hoje, inicioDaSemana, inicioDoMes, somarDias } from '../../core/dominio/data';
import { Dieta } from './dieta';
import { RefeicaoResponse } from './refeicao.model';
import { RegistroDiarioResponse } from './registro-diario.model';

const REFEICOES = `${environment.apiUrl}/refeicoes`;
const DIARIO = `${environment.apiUrl}/registros-diarios`;

// As datas vem das mesmas funcoes que a tela usa. Se o calculo delas esta certo e o
// core/dominio/data.spec.ts que testa, com datas fixas.
const SEMANA = inicioDaSemana(hoje());
const QUARTA = somarDias(SEMANA, 3);
const QUINTA = somarDias(SEMANA, 4);

/** Segundo modelo, com acento no titulo, para testar a busca. */
const CAFE_MODELO: RefeicaoResponse = {
  id: 4,
  titulo: 'Café da manhã',
  icone: 'ref-manha',
  itens: [],
  custoTotal: 0.76,
};

const ALMOCO_MODELO: RefeicaoResponse = {
  id: 3,
  titulo: 'Almoço',
  icone: 'ref-almoco',
  itens: [
    {
      id: 9,
      itemMercadoId: 7,
      nome: 'Peito de frango',
      quantidadeConsumida: 150,
      unidade: 'G',
      custo: 2.835,
      itemAtivo: true,
    },
  ],
  custoTotal: 2.835,
};

/** Registro de hoje do modelo acima: R$ 18,90 por 1 kg vezes 150 g da R$ 2,835. */
const ALMOCO_DE_HOJE: RegistroDiarioResponse = {
  id: 100,
  data: hoje(),
  titulo: 'Almoço',
  icone: 'ref-almoco',
  refeicaoOrigemId: 3,
  itens: [
    {
      id: 55,
      itemMercadoId: 7,
      nome: 'Peito de frango',
      quantidadeConsumida: 150,
      unidade: 'G',
      custo: 2.835,
      itemAtivo: true,
    },
  ],
  custoTotal: 2.835,
  itensSemPreco: [],
};

/** O mesmo registro depois do consumo virar 200 g: 0,0189 vezes 200 da R$ 3,78. */
const ALMOCO_AJUSTADO: RegistroDiarioResponse = {
  ...ALMOCO_DE_HOJE,
  itens: [{ ...ALMOCO_DE_HOJE.itens[0], quantidadeConsumida: 200, custo: 3.78 }],
  custoTotal: 3.78,
};

const JANTAR_DE_QUARTA: RegistroDiarioResponse = {
  id: 90,
  data: QUARTA,
  titulo: 'Jantar',
  icone: 'ref-jantar',
  refeicaoOrigemId: 5,
  itens: [],
  custoTotal: 3.94,
  itensSemPreco: [],
};

describe('Dieta — seção Diário', () => {
  let fixture: ComponentFixture<Dieta>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dieta],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // A secao vem da rota /diario e nao de um botao na tela.
        { provide: ActivatedRoute, useValue: { snapshot: { data: { aba: 'diario' } } } },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dieta);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  /** A carga da semana pede o intervalo mais o dia anterior a ele. */
  function cargaDaSemana(inicio: DataIso) {
    return http.expectOne(
      (requisicao) =>
        requisicao.method === 'GET' &&
        requisicao.url === DIARIO &&
        requisicao.params.get('inicio') === somarDias(inicio, -1) &&
        requisicao.params.get('fim') === somarDias(inicio, 6)
    );
  }

  /**
   * Responde as duas cargas que o componente dispara na secao Diario: a biblioteca, que e de
   * onde saem as refeicoes que o usuario registra, e a semana.
   */
  function abrirDiario(
    registros: RegistroDiarioResponse[] = [],
    biblioteca: RefeicaoResponse[] = [ALMOCO_MODELO]
  ): void {
    http.expectOne({ method: 'GET', url: REFEICOES }).flush(biblioteca);
    cargaDaSemana(SEMANA).flush(registros);
    fixture.detectChanges();
  }

  function colunas(): HTMLButtonElement[] {
    return Array.from(tela().querySelectorAll<HTMLButtonElement>('.day'));
  }

  /** O indice 0 e o domingo: a semana da aplicacao comeca ali. */
  function selecionar(indice: number): void {
    colunas()[indice].click();
    fixture.detectChanges();
  }

  it('carrega a semana ao entrar na aba e desenha sete colunas', () => {
    abrirDiario([ALMOCO_DE_HOJE]);

    expect(colunas().length).toBe(7);
    expect(colunas()[0].querySelector('.dw')?.textContent?.trim()).toBe('Dom');
    expect(colunas()[6].querySelector('.dw')?.textContent?.trim()).toBe('Sáb');
    expect(tela().querySelector('.subtitle')?.textContent?.trim()).toBe('O que você comeu cada dia');
  });

  it('marca com ponto apenas os dias que têm registro', () => {
    abrirDiario([JANTAR_DE_QUARTA]);

    const comPonto = colunas()
      .map((coluna, indice) => (coluna.querySelector('.dt.off') ? null : indice))
      .filter((indice) => indice !== null);

    expect(comPonto).toEqual([3]);
  });

  it('lista as refeições do dia com o custo e soma o custo do dia', () => {
    abrirDiario([ALMOCO_DE_HOJE, { ...JANTAR_DE_QUARTA, data: hoje(), id: 101 }]);

    expect(Array.from(tela().querySelectorAll('.meal .t'), (n) => n.textContent?.trim())).toEqual([
      'Almoço',
      'Jantar',
    ]);
    expect(tela().querySelector('.meal .p')?.textContent?.trim()).toBe('R$ 2,84');

    // 2,835 + 3,94 = 6,775, arredondado so na hora de exibir.
    expect(tela().querySelector('.hero .val')?.textContent?.trim()).toBe('R$ 6,78');
    expect(tela().querySelector('.hero .lbl')?.textContent?.trim()).toBe('Custo do dia');
  });

  it('troca o dia selecionado sem ir ao servidor de novo', () => {
    abrirDiario([JANTAR_DE_QUARTA]);

    selecionar(3);
    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Jantar');
    expect(colunas()[3].classList).toContain('on');

    // A semana inteira ja veio na primeira resposta.
    selecionar(5);
    expect(tela().querySelector('.meal')).toBeNull();
    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição registrada'
    );
  });

  it('só oferece repetir o dia anterior quando há o que copiar', () => {
    abrirDiario([JANTAR_DE_QUARTA]);

    // Na sexta a quinta esta vazia, entao nao tem de onde copiar.
    selecionar(5);
    expect(tela().querySelector('.btn-soft.solto')).toBeNull();

    // Na quinta a quarta tem registro.
    selecionar(4);
    const repetir = tela().querySelector<HTMLButtonElement>('.btn-soft.solto')!;
    expect(repetir.textContent).toContain('Repetir refeições de');

    repetir.click();
    fixture.detectChanges();

    const copia = http.expectOne({ method: 'POST', url: `${DIARIO}/duplicar-dia-anterior` });
    expect(copia.request.body).toEqual({ data: QUINTA });

    copia.flush([{ ...JANTAR_DE_QUARTA, id: 91, data: QUINTA }]);
    fixture.detectChanges();

    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Jantar');
    expect(tela().querySelector('.hero .val')?.textContent?.trim()).toBe('R$ 3,94');
  });

  it('registra no dia uma refeição escolhida na biblioteca', () => {
    abrirDiario([]);
    selecionar(4);

    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Adicionar ao dia');

    tela().querySelector<HTMLButtonElement>('.pick')!.click();
    fixture.detectChanges();

    const registro = http.expectOne({ method: 'POST', url: DIARIO });
    expect(registro.request.body).toEqual({ data: QUINTA, refeicaoId: 3 });

    registro.flush({ ...ALMOCO_DE_HOJE, id: 110, data: QUINTA });
    fixture.detectChanges();

    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Almoço');
    expect(colunas()[4].querySelector('.dt.off')).toBeNull();
  });

  it('busca filtra os modelos oferecidos para o dia e ignora acento', () => {
    abrirDiario([], [ALMOCO_MODELO, CAFE_MODELO]);

    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    const nomes = () =>
      Array.from(tela().querySelectorAll('.pick .t'), (n) => n.textContent?.trim());

    function buscar(termo: string): void {
      const campo = tela().querySelector<HTMLInputElement>('.sh-b .search input')!;
      campo.value = termo;
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    expect(nomes()).toEqual(['Almoço', 'Café da manhã']);

    buscar('cafe');
    expect(nomes()).toEqual(['Café da manhã']);

    buscar('ceia');
    expect(nomes()).toEqual([]);
    expect(tela().querySelector('.sh-b .empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição encontrada'
    );

    tela().querySelector<HTMLButtonElement>('.sh-b .empty .btn-out')!.click();
    fixture.detectChanges();
    expect(nomes()).toEqual(['Almoço', 'Café da manhã']);
  });

  it('o menu da refeição do dia leva ao ajuste de quantidade', () => {
    abrirDiario([ALMOCO_DE_HOJE]);

    tela().querySelector<HTMLButtonElement>('.meal .kb')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Almoço');

    tela().querySelector<HTMLButtonElement>('.btn-soft.espaco')!.click();
    fixture.detectChanges();

    // E a mesma tela de ajuste que abre ao tocar na linha, com a quantidade daquele dia.
    expect(tela().querySelector<HTMLInputElement>('.qi')!.value).toBe('150');
    expect(tela().querySelector('.note')?.textContent).toContain(
      'O modelo salvo em Refeições e os outros dias não mudam'
    );
  });

  it('aponta para Refeições quando não há modelo para registrar', () => {
    abrirDiario([], []);

    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    const vazio = tela().querySelector('app-bottom-sheet .empty')!;
    expect(vazio.querySelector('h4')?.textContent?.trim()).toBe('Nenhuma refeição cadastrada');
    // O caminho de saida e a outra rota e nao uma aba dessa mesma tela.
    expect(vazio.querySelector<HTMLAnchorElement>('.btn-out')!.getAttribute('href')).toBe(
      '/refeicoes'
    );
  });

  it('ajusta a quantidade do dia sem tocar no modelo da biblioteca', () => {
    abrirDiario([ALMOCO_DE_HOJE]);

    tela().querySelector<HTMLButtonElement>('.meal .linha')!.click();
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Almoço');
    expect(tela().querySelector('.note')?.textContent).toContain(
      'O modelo salvo em Refeições e os outros dias não mudam'
    );
    expect(tela().querySelector('.live .v')?.textContent?.trim()).toBe('R$ 2,84');

    const campo = tela().querySelector<HTMLInputElement>('.qi')!;
    expect(campo.value).toBe('150');

    campo.value = '200';
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const ajuste = http.expectOne({ method: 'PATCH', url: `${DIARIO}/100/itens/55` });
    expect(ajuste.request.body).toEqual({ quantidadeConsumida: 200, unidade: 'G' });

    ajuste.flush(ALMOCO_AJUSTADO);
    fixture.detectChanges();

    expect(tela().querySelector('.live .v')?.textContent?.trim()).toBe('R$ 3,78');

    tela().querySelector<HTMLButtonElement>('.btn')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('.hero .val')?.textContent?.trim()).toBe('R$ 3,78');

    // A biblioteca nao e recarregada nem alterada.
    http.expectNone({ method: 'GET', url: REFEICOES });
    http.expectNone({ method: 'PUT', url: `${REFEICOES}/3` });

    // O modelo que esta na memoria continua com os 150 g originais. Confere o estado e nao a
    // tela, porque a Biblioteca virou outra rota: nao tem mais aba para abrir aqui e o painel
    // dela nao esta montado nessa instancia.
    expect(fixture.componentInstance['refeicoes']()[0].itens[0]).toEqual(
      jasmine.objectContaining({ quantidadeConsumida: 150, custo: 2.835 })
    );
  });

  it('recusa quantidade zerada e devolve o valor anterior ao campo', () => {
    abrirDiario([ALMOCO_DE_HOJE]);

    tela().querySelector<HTMLButtonElement>('.meal .linha')!.click();
    fixture.detectChanges();

    const campo = tela().querySelector<HTMLInputElement>('.qi')!;
    campo.value = '0';
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    http.expectNone({ method: 'PATCH', url: `${DIARIO}/100/itens/55` });
    expect(tela().querySelector('.erro-form')?.textContent?.trim()).toBe(
      'A quantidade deve ser maior que zero.'
    );
    expect(campo.value).toBe('150');
  });

  it('sinaliza o item sem preço em vez de contá-lo como R$ 0,00', () => {
    const comFalta: RegistroDiarioResponse = {
      ...ALMOCO_DE_HOJE,
      itens: [{ ...ALMOCO_DE_HOJE.itens[0], itemMercadoId: null, custo: null }],
      custoTotal: 0,
      itensSemPreco: ['Peito de frango'],
    };

    abrirDiario([comFalta]);

    expect(tela().querySelector('.aviso-dia')?.textContent).toContain('Peito de frango');
    expect(tela().querySelector('.meal.sem-custo')).not.toBeNull();
    expect(tela().querySelector('.meal .s')?.textContent).toContain('1 sem preço');

    tela().querySelector<HTMLButtonElement>('.meal .linha')!.click();
    fixture.detectChanges();

    expect(tela().querySelector('.ai.sem-custo .s')?.textContent).toContain('sem preço');
    expect(tela().querySelector('.note.aviso')?.textContent).toContain('ficou de fora do total');
  });

  it('remove a refeição do dia depois de confirmar', () => {
    abrirDiario([ALMOCO_DE_HOJE]);

    // O botao de tres pontinhos abre o menu. Remover fica dentro dele, nunca a um toque so.
    tela().querySelector<HTMLButtonElement>('.meal .kb')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Almoço');

    tela().querySelector<HTMLButtonElement>('.btn-soft.destrutivo')!.click();
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Remover do dia');
    http.expectNone({ method: 'DELETE', url: `${DIARIO}/100` });

    tela().querySelector<HTMLButtonElement>('.btn.destrutivo')!.click();
    fixture.detectChanges();

    http
      .expectOne({ method: 'DELETE', url: `${DIARIO}/100` })
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição registrada'
    );
  });

  it('navega a semana levando a seleção junto', () => {
    abrirDiario([ALMOCO_DE_HOJE]);
    const rotuloInicial = tela().querySelector('.weeknav .rng')?.textContent?.trim();

    tela().querySelector<HTMLButtonElement>('.weeknav button[aria-label="Semana anterior"]')!.click();
    fixture.detectChanges();
    cargaDaSemana(somarDias(SEMANA, -7)).flush([]);
    fixture.detectChanges();

    expect(tela().querySelector('.weeknav .rng')?.textContent?.trim()).not.toBe(rotuloInicial);

    // A faixa de cima e a lista de baixo continuam falando do mesmo dia, entao uma coluna
    // continua marcada.
    expect(colunas().filter((coluna) => coluna.classList.contains('on')).length).toBe(1);

    tela().querySelector<HTMLButtonElement>('.weeknav button[aria-label="Próxima semana"]')!.click();
    fixture.detectChanges();
    cargaDaSemana(SEMANA).flush([ALMOCO_DE_HOJE]);
    fixture.detectChanges();

    expect(tela().querySelector('.weeknav .rng')?.textContent?.trim()).toBe(rotuloInicial!);
    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Almoço');
  });

  /** Abre o calendario e responde a carga dos pontinhos da grade de 42 dias. */
  function abrirCalendario(registros: RegistroDiarioResponse[] = []) {
    tela().querySelector<HTMLButtonElement>('.weeknav .rng')!.click();
    fixture.detectChanges();

    const carga = http.expectOne(
      (requisicao) => requisicao.method === 'GET' && requisicao.url === DIARIO
    );
    carga.flush(registros);
    fixture.detectChanges();
    return carga;
  }

  function celulas(): HTMLButtonElement[] {
    return Array.from(tela().querySelectorAll<HTMLButtonElement>('.cal-dia'));
  }

  it('o calendário marca com ponto verde os dias que têm refeição registrada', () => {
    abrirDiario([JANTAR_DE_QUARTA]);
    abrirCalendario([JANTAR_DE_QUARTA]);

    const comPonto = celulas().filter((celula) => !celula.querySelector('.dt.off'));

    // So a quarta da semana atual. O pontinho vem do registro e nao do dia selecionado.
    expect(comPonto.length).toBe(1);
    expect(comPonto[0].getAttribute('aria-label')).toContain('com refeições registradas');
  });

  it('o calendário busca a grade inteira, não só a semana visível', () => {
    abrirDiario([]);
    const carga = abrirCalendario([]);

    // 42 celulas sao 6 semanas. Sem isso os dias fora da semana aberta ficariam sem pontinho.
    expect(celulas().length).toBe(42);

    // A grade abre no mes do dia selecionado e comeca na segunda anterior ao dia 1.
    const inicioDaGrade = inicioDaSemana(inicioDoMes(hoje()));
    expect(carga.request.params.get('inicio')).toBe(inicioDaGrade);
    expect(carga.request.params.get('fim')).toBe(somarDias(inicioDaGrade, 41));
  });

  it('dia sem registro não recebe ponto e diz isso a quem lê a tela', () => {
    abrirDiario([]);
    abrirCalendario([]);

    expect(celulas().every((celula) => celula.querySelector('.dt.off') !== null)).toBeTrue();
    expect(celulas()[0].getAttribute('aria-label')).toContain('sem registro');
  });

  it('falha ao buscar os marcadores não impede navegar pelo calendário', () => {
    abrirDiario([]);

    tela().querySelector<HTMLButtonElement>('.weeknav .rng')!.click();
    fixture.detectChanges();
    http
      .expectOne((requisicao) => requisicao.method === 'GET' && requisicao.url === DIARIO)
      .error(new ProgressEvent('erro'), { status: 500 });
    fixture.detectChanges();

    // A grade continua funcionando, so que sem os pontinhos. Eles sao detalhe e nao conteudo.
    expect(celulas().length).toBe(42);
    expect(celulas().every((celula) => celula.querySelector('.dt.off') !== null)).toBeTrue();
  });

  it('oferece tentar de novo quando o diário não carrega', () => {
    http.expectOne({ method: 'GET', url: REFEICOES }).flush([]);
    cargaDaSemana(SEMANA).error(new ProgressEvent('erro'), { status: 500 });
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe('Não deu para carregar');

    tela().querySelector<HTMLButtonElement>('.btn-out')!.click();
    fixture.detectChanges();
    cargaDaSemana(SEMANA).flush([ALMOCO_DE_HOJE]);
    fixture.detectChanges();

    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Almoço');
  });
});
