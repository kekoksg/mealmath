import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ItemMercadoResponse } from './item-mercado.model';
import { Mercado } from './mercado';

const URL = `${environment.apiUrl}/itens-mercado`;

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

const OVOS: ItemMercadoResponse = {
  id: 12,
  nome: 'Ovos',
  preco: 12,
  quantidadeEmbalagem: 12,
  unidade: 'UN',
  custoUnitario: 1,
  unidadeBase: 'un',
  categoria: 'PROTEINA',
  ativo: true,
  atualizadoEm: '2026-08-03T14:02:11.412Z',
};

describe('Mercado', () => {
  let fixture: ComponentFixture<Mercado>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Mercado],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Mercado);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function listar(itens: ItemMercadoResponse[]): void {
    http.expectOne({ method: 'GET', url: URL }).flush(itens);
    fixture.detectChanges();
  }

  function preencherFormulario(valores: Record<string, unknown>): void {
    fixture.componentInstance['form'].patchValue(valores);
    fixture.detectChanges();
  }

  it('mostra o custo unitário por 100 g e por unidade', () => {
    listar([FRANGO, OVOS]);

    const precos = tela().querySelectorAll('.mkt .v .p');
    // R$ 0,0189/g com 2 casas viraria R$ 0,02/g, por isso a lista mostra o custo de 100 g.
    expect(precos[0].textContent?.trim()).toBe('R$ 1,89 /100g');
    expect(precos[1].textContent?.trim()).toBe('R$ 1,00 /un');

    const embalagens = tela().querySelectorAll('.mkt .bd .s');
    expect(embalagens[0].textContent?.trim()).toBe('R$ 18,90 · 1 kg');
    expect(embalagens[1].textContent?.trim()).toBe('R$ 12,00 · 12 un');
  });

  it('estado vazio convida a cadastrar e abre o formulário', () => {
    listar([]);

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe('Nenhum item de mercado');
    expect(tela().querySelector('.search')).toBeNull();

    tela().querySelector<HTMLButtonElement>('.empty .btn-out')!.click();
    fixture.detectChanges();

    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();
  });

  it('filtra a lista pelo termo buscado, ignorando acento', () => {
    listar([FRANGO, OVOS]);

    const busca = tela().querySelector<HTMLInputElement>('.search input')!;
    busca.value = 'frango';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const nomes = Array.from(tela().querySelectorAll('.mkt .t'), (n) => n.textContent?.trim());
    expect(nomes).toEqual(['Peito de frango']);

    busca.value = 'ovoss';
    busca.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe('Nada encontrado');
  });

  it('recalcula o custo unitário enquanto o formulário é preenchido', () => {
    listar([]);
    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    const aoVivo = () => tela().querySelector('.live .v')?.textContent?.trim();

    // Sem preco e sem quantidade nao existe custo. Mostrar R$ 0,00 esconderia o valor real.
    expect(aoVivo()).toBe('—');

    preencherFormulario({ preco: 18.9, quantidadeEmbalagem: 1, unidade: 'KG' });
    expect(aoVivo()).toBe('R$ 1,89 /100g');

    // O mesmo produto comprado em gramas tem que dar exatamente o mesmo custo.
    preencherFormulario({ quantidadeEmbalagem: 1000, unidade: 'G' });
    expect(aoVivo()).toBe('R$ 1,89 /100g');

    preencherFormulario({ preco: 5.2, quantidadeEmbalagem: 1, unidade: 'L' });
    expect(aoVivo()).toBe('R$ 0,52 /100mL');

    preencherFormulario({ preco: 12, quantidadeEmbalagem: 12, unidade: 'UN' });
    expect(aoVivo()).toBe('R$ 1,00 /un');

    // Quantidade zerada volta para o traco, em vez de tentar dividir por zero.
    preencherFormulario({ quantidadeEmbalagem: 0 });
    expect(aoVivo()).toBe('—');
  });

  it('não envia o cadastro com preço ou quantidade inválidos', () => {
    listar([]);
    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    preencherFormulario({ nome: 'Frango', preco: 0, quantidadeEmbalagem: 1, unidade: 'KG' });
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone({ method: 'POST', url: URL });
    expect(tela().querySelector('.erro-campo')?.textContent?.trim()).toBe(
      'O preço deve ser maior que zero.'
    );
  });

  it('cadastra o item e recarrega a lista', () => {
    listar([]);
    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    preencherFormulario({
      nome: '  Peito de frango  ',
      preco: 18.9,
      quantidadeEmbalagem: 1,
      unidade: 'KG',
      categoria: 'PROTEINA',
    });
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const criacao = http.expectOne({ method: 'POST', url: URL });
    expect(criacao.request.body).toEqual({
      nome: 'Peito de frango',
      preco: 18.9,
      quantidadeEmbalagem: 1,
      unidade: 'KG',
      categoria: 'PROTEINA',
    });
    criacao.flush(FRANGO);
    fixture.detectChanges();

    listar([FRANGO]);
    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(tela().querySelectorAll('.mkt').length).toBe(1);
  });

  it('abre a edição com o item carregado e mostra o histórico de preço', () => {
    listar([FRANGO]);

    tela().querySelector<HTMLButtonElement>('.mkt')!.click();
    fixture.detectChanges();

    http.expectOne({ method: 'GET', url: `${URL}/7/historico` }).flush([
      {
        id: 2,
        preco: 16.5,
        quantidadeEmbalagem: 1,
        unidade: 'KG',
        custoUnitario: 0.0165,
        unidadeBase: 'g',
        substituidoEm: '2026-07-30T11:12:00Z',
      },
    ]);
    fixture.detectChanges();

    expect(tela().querySelector('.sh-h h3')?.textContent?.trim()).toBe('Editar item');
    expect(tela().querySelector<HTMLInputElement>('#item-nome')!.value).toBe('Peito de frango');
    expect(tela().querySelector('.hist')?.textContent?.trim()).toBe(
      'Histórico de preço: R$ 16,50 → R$ 18,90'
    );

    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectOne({ method: 'PUT', url: `${URL}/7` }).flush(FRANGO);
    fixture.detectChanges();
    listar([FRANGO]);
  });

  it('mostra a mensagem da API quando o nome já existe', () => {
    listar([]);
    tela().querySelector<HTMLButtonElement>('.fab')!.click();
    fixture.detectChanges();

    preencherFormulario({ nome: 'Peito de frango', preco: 18.9, quantidadeEmbalagem: 1 });
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http
      .expectOne({ method: 'POST', url: URL })
      .flush(
        { mensagem: 'Já existe um item com esse nome.' },
        { status: 409, statusText: 'Conflict' }
      );
    fixture.detectChanges();

    expect(tela().querySelector('.erro-form')?.textContent?.trim()).toBe(
      'Já existe um item com esse nome.'
    );
    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();
  });

  it('oferece tentar de novo quando a listagem falha', () => {
    http.expectOne({ method: 'GET', url: URL }).error(new ProgressEvent('erro'), { status: 500 });
    fixture.detectChanges();

    expect(tela().querySelector('.empty h4')?.textContent?.trim()).toBe('Não deu para carregar');

    tela().querySelector<HTMLButtonElement>('.btn-out')!.click();
    fixture.detectChanges();
    listar([FRANGO]);

    expect(tela().querySelectorAll('.mkt').length).toBe(1);
  });
});
