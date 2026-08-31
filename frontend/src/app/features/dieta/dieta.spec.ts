import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ItemMercadoResponse } from '../mercado/item-mercado.model';
import { Dieta } from './dieta';
import { RefeicaoResponse } from './refeicao.model';

const REFEICOES = `${environment.apiUrl}/refeicoes`;
const MERCADO = `${environment.apiUrl}/itens-mercado`;
const REGISTROS = `${environment.apiUrl}/registros-diarios`;

/** R$ 18,90 por 1 kg da R$ 0,0189 por g. */
const FRANGO: ItemMercadoResponse = {
  id: 7,
  nome: 'Peito de frango',
  preco: 18.9,
  quantidadeEmbalagem: 1,
  unidade: 'KG',
  custoUnitario: 0.0189,
  unidadeBase: 'g',
  categoria: 'PROTEINA',
  ativo: true,
  atualizadoEm: '2026-08-03T14:02:11.412Z',
};

/** R$ 4,50 por 1 un. Fracao de unidade e valida e nao pode ser arredondada. */
const BROCOLIS: ItemMercadoResponse = {
  id: 12,
  nome: 'Brócolis',
  preco: 4.5,
  quantidadeEmbalagem: 1,
  unidade: 'UN',
  custoUnitario: 4.5,
  unidadeBase: 'un',
  categoria: 'HORTIFRUTI',
  ativo: true,
  atualizadoEm: '2026-08-03T14:02:11.412Z',
};

const ALMOCO: RefeicaoResponse = {
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

const CAFE: RefeicaoResponse = {
  id: 1,
  titulo: 'Café da manhã',
  icone: 'ref-manha',
  itens: [],
  custoTotal: 0.76,
};

const JANTAR: RefeicaoResponse = {
  id: 5,
  titulo: 'Jantar',
  icone: 'ref-jantar',
  itens: [],
  custoTotal: 3.94,
};

describe('Dieta — aba Biblioteca', () => {
  let fixture: ComponentFixture<Dieta>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dieta],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dieta);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function listar(refeicoes: RefeicaoResponse[]): void {
    http.expectOne({ method: 'GET', url: REFEICOES }).flush(refeicoes);
    fixture.detectChanges();
  }

  /** Abre o formulario e responde a carga do mercado que ele dispara. */
  function abrirFormulario(itens: ItemMercadoResponse[] = [FRANGO, BROCOLIS]): void {
    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: MERCADO }).flush(itens);
    fixture.detectChanges();
  }

  function titulos(): (string | undefined)[] {
    return Array.from(tela().querySelectorAll('.meal .t'), (n) => n.textContent?.trim());
  }

  it('serve a seção Refeições e não busca o diário', () => {
    listar([ALMOCO]);

    expect(tela().querySelector('.title')?.textContent?.trim()).toBe('Refeições');
    expect(tela().querySelector('.subtitle')?.textContent?.trim()).toBe(
      'Modelos salvos de refeições'
    );

    // A semana so e buscada na rota /diario. Aqui essa chamada seria desperdicio.
    http.expectNone((requisicao) => requisicao.url === REGISTROS);
  });

  it('lista as refeições com o custo por refeição e a contagem de itens', () => {
    listar([ALMOCO]);

    expect(tela().querySelector('.meal .t')?.textContent?.trim()).toBe('Almoço');
    expect(tela().querySelector('.meal .s')?.textContent?.trim()).toBe('1 item');
    expect(tela().querySelector('.meal .v .p')?.textContent?.trim()).toBe('R$ 2,84');
    expect(tela().querySelector('.meal .v .u')?.textContent?.trim()).toBe('/ refeição');
  });

  it('estado vazio convida a criar e abre o formulário', () => {
    listar([]);

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição cadastrada'
    );
    expect(tela().querySelector('.search')).toBeNull();

    tela().querySelector<HTMLButtonElement>('.empty .btn-out')!.click();
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: MERCADO }).flush([]);
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Nova refeição');
  });

  it('filtra a biblioteca pelo termo buscado, ignorando acento', () => {
    listar([CAFE, ALMOCO]);

    const busca = tela().querySelector<HTMLInputElement>('.search input')!;
    busca.value = 'almoco';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(titulos()).toEqual(['Almoço']);

    busca.value = 'jantar';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição encontrada'
    );
  });

  it('reordena arrastando uma refeição sobre a outra', () => {
    listar([CAFE, ALMOCO]);
    expect(titulos()).toEqual(['Café da manhã', 'Almoço']);

    const linhas = tela().querySelectorAll<HTMLElement>('.meal');
    linhas[1].dispatchEvent(new DragEvent('dragstart'));
    linhas[0].dispatchEvent(new DragEvent('dragover'));
    fixture.detectChanges();

    expect(titulos()).toEqual(['Almoço', 'Café da manhã']);

    linhas[1].dispatchEvent(new DragEvent('dragend'));
    fixture.detectChanges();
    expect(tela().querySelector('.meal.drag')).toBeNull();
  });

  it('a alça move a refeição pelo teclado e a busca desliga o reordenar', () => {
    listar([CAFE, ALMOCO, JANTAR]);
    expect(titulos()).toEqual(['Café da manhã', 'Almoço', 'Jantar']);

    /** Pega a seta da alca da refeicao que esta na posicao informada, comecando do zero. */
    function seta(indice: number, key: 'ArrowUp' | 'ArrowDown'): void {
      tela()
        .querySelectorAll<HTMLElement>('.meal .grip')
        [indice].dispatchEvent(new KeyboardEvent('keydown', { key }));
      fixture.detectChanges();
    }

    // Duas setas seguidas levam o Jantar do fim para o comeco. Andar so uma posicao por vez
    // nao serviria de nada.
    seta(2, 'ArrowUp');
    expect(titulos()).toEqual(['Café da manhã', 'Jantar', 'Almoço']);
    seta(1, 'ArrowUp');
    expect(titulos()).toEqual(['Jantar', 'Café da manhã', 'Almoço']);

    seta(0, 'ArrowDown');
    expect(titulos()).toEqual(['Café da manhã', 'Jantar', 'Almoço']);

    // Na ponta da lista a seta nao faz nada, em vez de embaralhar tudo.
    seta(0, 'ArrowUp');
    expect(titulos()).toEqual(['Café da manhã', 'Jantar', 'Almoço']);

    // Arrastando com a lista filtrada, a refeicao iria para uma posicao que ninguem ve.
    const busca = tela().querySelector<HTMLInputElement>('.search input')!;
    busca.value = 'almo';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(tela().querySelector('.meal .grip')).toBeNull();
  });

  it('soma o custo ao vivo conforme os itens do mercado entram na refeição', () => {
    listar([]);
    abrirFormulario();

    const aoVivo = () => tela().querySelector('.live .v')?.textContent?.trim();

    // Sem item nao existe custo. R$ 0,00 daria a entender que a refeicao ja custa alguma coisa.
    expect(aoVivo()).toBe('—');

    // Primeiro item: frango, 150 g. 0,0189 vezes 150 da R$ 2,835.
    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Peito de frango');
    fixture.componentInstance['formItem'].patchValue({ quantidadeConsumida: 150, unidade: 'G' });
    fixture.detectChanges();
    expect(aoVivo()).toBe('R$ 2,84');

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(aoVivo()).toBe('R$ 2,84');

    // Segundo item: brocolis, 0,5 un, que da R$ 2,25. Total de 2,835 + 2,25 = R$ 5,085.
    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[1].click();
    fixture.detectChanges();
    fixture.componentInstance['formItem'].patchValue({ quantidadeConsumida: 0.5, unidade: 'UN' });
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(aoVivo()).toBe('R$ 5,09');
    expect(
      Array.from(tela().querySelectorAll('.ai .s'), (n) => n.textContent?.replace(/\s+/g, ' ').trim())
    ).toEqual(['150 g · R$ 2,84', '0,5 un · R$ 2,25']);

    // Tirando o brocolis, o total volta a ser so o do primeiro item.
    tela().querySelectorAll<HTMLButtonElement>('.ai .rm')[1].click();
    fixture.detectChanges();
    expect(aoVivo()).toBe('R$ 2,84');
  });

  it('a busca filtra o catálogo do mercado e ignora acento', () => {
    listar([]);
    abrirFormulario();

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();

    const nomes = () =>
      Array.from(tela().querySelectorAll('.pick .t'), (n) => n.textContent?.trim());

    function buscar(termo: string): void {
      const campo = tela().querySelector<HTMLInputElement>('.sh-b .search input')!;
      campo.value = termo;
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    expect(nomes()).toEqual(['Peito de frango', 'Brócolis']);

    // Quem digita no celular nao coloca acento, entao "brocolis" tem que achar "Brocolis".
    buscar('brocolis');
    expect(nomes()).toEqual(['Brócolis']);

    // Quando nada bate com a busca, entra um estado vazio com o que fazer, e nao uma tela vazia.
    buscar('picanha');
    expect(nomes()).toEqual([]);
    // Procuro dentro do painel, porque a biblioteca vazia tem um .empty proprio antes desse.
    expect(tela().querySelector('.sh-b .empty h4')?.textContent?.trim()).toBe(
      'Nenhum item encontrado'
    );

    tela().querySelector<HTMLButtonElement>('.sh-b .empty .btn-out')!.click();
    fixture.detectChanges();
    expect(nomes()).toEqual(['Peito de frango', 'Brócolis']);

    // Reabrir a escolha comeca do zero, senao o termo da vez anterior esconderia o catalogo.
    buscar('frango');
    expect(nomes()).toEqual(['Peito de frango']);
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();
    fixture.componentInstance['formItem'].patchValue({ quantidadeConsumida: 150, unidade: 'G' });
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    expect(nomes()).toEqual(['Peito de frango', 'Brócolis']);
  });

  it('só oferece unidades da mesma grandeza da embalagem', () => {
    listar([]);
    abrirFormulario();

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();

    // O frango e vendido em kg, entao consumir em mL seria entrada invalida.
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();
    expect(Array.from(tela().querySelectorAll('option'), (o) => o.textContent?.trim())).toEqual([
      'kg',
      'g',
    ]);

    tela().querySelector<HTMLButtonElement>('.sh-h button')!.click();
    fixture.detectChanges();
    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[1].click();
    fixture.detectChanges();
    expect(Array.from(tela().querySelectorAll('option'), (o) => o.textContent?.trim())).toEqual([
      'un',
    ]);
  });

  it('não adiciona item com quantidade zerada', () => {
    listar([]);
    abrirFormulario();

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();

    fixture.componentInstance['formItem'].patchValue({ quantidadeConsumida: 0 });
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(tela().querySelector('.erro-campo')?.textContent?.trim()).toBe(
      'A quantidade deve ser maior que zero.'
    );
    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Peito de frango');
  });

  it('recusa salvar sem título ou sem nenhum item', () => {
    listar([]);
    abrirFormulario();

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone({ method: 'POST', url: REFEICOES });
    expect(tela().querySelector('.erro-campo')?.textContent?.trim()).toBe(
      'Dê um título à refeição.'
    );

    fixture.componentInstance['form'].patchValue({ titulo: 'Almoço' });
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone({ method: 'POST', url: REFEICOES });
    expect(tela().querySelector('.erro-form')?.textContent?.trim()).toBe(
      'Adicione ao menos um item do mercado à refeição.'
    );
  });

  it('cria a refeição com o ícone escolhido e recarrega a lista', () => {
    listar([]);
    abrirFormulario();

    tela().querySelectorAll<HTMLButtonElement>('.icons button')[0].click();
    fixture.detectChanges();
    fixture.componentInstance['form'].patchValue({ titulo: '  Café da manhã  ' });

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();
    fixture.componentInstance['formItem'].patchValue({ quantidadeConsumida: 150, unidade: 'G' });
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const criacao = http.expectOne({ method: 'POST', url: REFEICOES });
    expect(criacao.request.body).toEqual({
      titulo: 'Café da manhã',
      icone: 'ref-manha',
      itens: [{ itemMercadoId: 7, quantidadeConsumida: 150, unidade: 'G' }],
    });
    criacao.flush(CAFE);
    fixture.detectChanges();

    listar([CAFE]);
    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(tela().querySelectorAll('.meal').length).toBe(1);
  });

  it('edita pelo menu carregando os itens já salvos e envia um PUT', () => {
    listar([ALMOCO]);

    tela().querySelector<HTMLButtonElement>('.meal .kb')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Almoço');

    tela().querySelectorAll<HTMLButtonElement>('.btn-soft')[0].click();
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: MERCADO }).flush([FRANGO, BROCOLIS]);
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Editar refeição');
    expect(tela().querySelector<HTMLInputElement>('#refeicao-titulo')!.value).toBe('Almoço');
    expect(tela().querySelector('.ai .t')?.textContent?.trim()).toBe('Peito de frango');
    expect(tela().querySelector('.live .v')?.textContent?.trim()).toBe('R$ 2,84');

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const atualizacao = http.expectOne({ method: 'PUT', url: `${REFEICOES}/3` });
    expect(atualizacao.request.body).toEqual({
      titulo: 'Almoço',
      icone: 'ref-almoco',
      itens: [{ itemMercadoId: 7, quantidadeConsumida: 150, unidade: 'G' }],
    });
    atualizacao.flush(ALMOCO);
    fixture.detectChanges();
    listar([ALMOCO]);
  });

  it('exclui pelo menu depois de confirmar', () => {
    listar([ALMOCO]);

    tela().querySelector<HTMLButtonElement>('.meal .kb')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.btn-soft')[1].click();
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Excluir refeição');
    http.expectNone({ method: 'DELETE', url: `${REFEICOES}/3` });

    tela().querySelector<HTMLButtonElement>('.btn.destrutivo')!.click();
    fixture.detectChanges();

    http.expectOne({ method: 'DELETE', url: `${REFEICOES}/3` }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    listar([]);
    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe(
      'Nenhuma refeição cadastrada'
    );
  });

  it('mostra a mensagem da API quando a gravação falha e mantém o sheet aberto', () => {
    listar([]);
    abrirFormulario();

    fixture.componentInstance['form'].patchValue({ titulo: 'Almoço' });
    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();
    tela().querySelectorAll<HTMLButtonElement>('.pick')[0].click();
    fixture.detectChanges();
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http
      .expectOne({ method: 'POST', url: REFEICOES })
      .flush(
        { mensagem: 'Item de mercado desativado.' },
        { status: 409, statusText: 'Conflict' }
      );
    fixture.detectChanges();

    expect(tela().querySelector('.erro-form')?.textContent?.trim()).toBe(
      'Item de mercado desativado.'
    );
    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();
  });

  it('oferece tentar de novo quando a biblioteca não carrega', () => {
    http
      .expectOne({ method: 'GET', url: REFEICOES })
      .error(new ProgressEvent('erro'), { status: 500 });
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe('Não deu para carregar');

    tela().querySelector<HTMLButtonElement>('.btn-out')!.click();
    fixture.detectChanges();
    listar([ALMOCO]);

    expect(tela().querySelectorAll('.meal').length).toBe(1);
  });

  it('aponta para o Mercado quando não há item para compor a refeição', () => {
    listar([]);
    abrirFormulario([]);

    tela().querySelector<HTMLButtonElement>('.btn-soft')!.click();
    fixture.detectChanges();

    // Procuro dentro do painel, porque o estado vazio da biblioteca continua atras do veu.
    expect(tela().querySelector('app-bottom-sheet .empty h4')?.textContent?.trim()).toBe(
      'Nenhum item de mercado'
    );
    // Sem item de mercado nao tem como montar refeicao, entao o estado vazio leva o usuario
    // para a tela de mercado.
    expect(
      tela().querySelector<HTMLAnchorElement>('app-bottom-sheet .empty .btn-out')!.getAttribute('href')
    ).toBe('/mercado');
  });
});
