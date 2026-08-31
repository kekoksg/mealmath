import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardResponse, MetaOrcamentoResponse } from '../dashboard/dashboard.model';
import { ItemMercadoResponse } from '../mercado/item-mercado.model';
import { RefeicaoResponse } from '../dieta/refeicao.model';
import { Perfil } from './perfil';

const URL_REFEICOES = `${environment.apiUrl}/refeicoes`;
const URL_ITENS = `${environment.apiUrl}/itens-mercado`;
const URL_DASHBOARD = `${environment.apiUrl}/dashboard`;
const URL_META = `${environment.apiUrl}/meta-orcamento`;
const URL_PERFIL = `${environment.apiUrl}/perfil`;
const URL_SENHA = `${environment.apiUrl}/perfil/senha`;

const CHAVE_TOKEN = 'dieta.token';

/** JWT com formato valido. O front nao confere a assinatura, so le os dados de dentro. */
function jwtFalso(payload: Record<string, unknown>): string {
  const bytes = new TextEncoder().encode(JSON.stringify(payload));
  const base64 = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `cabecalho.${base64}.assinatura`;
}

function tokenDe(claims: Record<string, unknown>): string {
  return jwtFalso({
    sub: '1',
    nome: 'Maria da Silva',
    email: 'maria@email.com',
    exp: Math.floor(Date.now() / 1000) + 28800,
    ...claims,
  });
}

const REFEICAO: RefeicaoResponse = {
  id: 3,
  titulo: 'Almoço',
  icone: 'ref-almoco',
  itens: [],
  custoTotal: 12.5,
};

const ITEM: ItemMercadoResponse = {
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

const MES: DashboardResponse = {
  periodo: 'MES',
  inicio: '2026-07-07',
  fim: '2026-08-05',
  custoTotal: 742.9,
  custoMedioPorDia: 24.763333333,
  comparativo: null,
  completude: { diasComRegistro: 18, totalDeDias: 30 },
  meta: null,
  composicaoPorCategoria: [],
  itensMaiorImpacto: [],
  altasDePreco: [],
  itensSemPreco: [],
};

const META: MetaOrcamentoResponse = {
  id: 2,
  valor: 450,
  periodo: 'MENSAL',
  atualizadoEm: '2026-08-01T09:30:00Z',
};

describe('Perfil', () => {
  let fixture: ComponentFixture<Perfil>;
  let http: HttpTestingController;

  /** Cria a tela com uma sessao aberta. O parametro claims ajusta o token dessa sessao. */
  function montar(claims: Record<string, unknown> = { criadoEm: '2026-01-15T10:30:00Z' }): void {
    localStorage.setItem(CHAVE_TOKEN, tokenDe(claims));

    TestBed.configureTestingModule({
      imports: [Perfil],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    http = TestBed.inject(HttpTestingController);
    // A sessao precisa existir antes da tela desenhar, porque e dela que sai o cabecalho.
    TestBed.inject(AuthService);
    fixture = TestBed.createComponent(Perfil);
    fixture.detectChanges();
  }

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CHAVE_TOKEN);
    TestBed.resetTestingModule();
  });

  function tela(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texto(seletor: string): string | undefined {
    return tela().querySelector(seletor)?.textContent?.replace(/\s+/g, ' ').trim();
  }

  /** Responde as quatro cargas que a tela dispara ao mesmo tempo. */
  function carregar(
    ajustes: {
      refeicoes?: RefeicaoResponse[];
      itens?: ItemMercadoResponse[];
      painel?: Partial<DashboardResponse>;
      meta?: MetaOrcamentoResponse | null;
    } = {}
  ): void {
    http.expectOne({ method: 'GET', url: URL_REFEICOES }).flush(ajustes.refeicoes ?? [REFEICAO]);
    http.expectOne({ method: 'GET', url: URL_ITENS }).flush(ajustes.itens ?? [ITEM]);

    const painel = http.expectOne((requisicao) => requisicao.url === URL_DASHBOARD);
    // O perfil olha o mes inteiro, que e a janela onde o custo por dia significa alguma coisa.
    expect(painel.request.params.get('periodo')).toBe('MES');
    painel.flush({ ...MES, ...ajustes.painel });

    http
      .expectOne({ method: 'GET', url: URL_META })
      .flush(ajustes.meta === undefined ? null : ajustes.meta);

    fixture.detectChanges();
  }

  it('mostra a identidade do token antes de a API responder', () => {
    montar();

    // Nada de tela em branco, porque o nome e o e-mail ja estao na sessao.
    expect(texto('.prof h1')).toBe('Maria da Silva');
    expect(texto('.prof .email')).toBe('maria@email.com');
    expect(texto('.carregando')).toBe('Carregando os dados da conta…');

    carregar();
    expect(tela().querySelector('.carregando')).toBeNull();
  });

  it('resume a conta e expõe a lacuna do diário junto da média', () => {
    montar();
    carregar();

    const numeros = Array.from(tela().querySelectorAll('.stats .n'), (n) => n.textContent?.trim());
    expect(numeros).toEqual(['1', '1', 'R$ 24,76']);

    const rotulos = Array.from(tela().querySelectorAll('.stats .l'), (n) => n.textContent?.trim());
    expect(rotulos).toEqual(['Refeição', 'Item', 'Custo/dia']);

    // A media divide pelos 30 dias da janela. Sem o contador, 18 dias anotados pareceriam
    // um mes inteiro muito barato.
    expect(texto('.stats-cap')).toBe('Média dos últimos 30 dias · 18 de 30 dias com registro');
  });

  it('conta sem nada cadastrado convida a começar pelo mercado', () => {
    montar();
    carregar({ refeicoes: [], itens: [], painel: { custoTotal: 0, custoMedioPorDia: 0 } });

    expect(tela().querySelector('.stats')).toBeNull();
    expect(texto('.empty h4')).toBe('Sua conta ainda está vazia');
    expect(tela().querySelector<HTMLAnchorElement>('.empty .btn-out')!.getAttribute('href')).toBe(
      '/mercado'
    );
  });

  it('sem meta definida a linha convida a defini-la', () => {
    montar();
    carregar({ meta: null });

    expect(texto('.info .l')).toBe('Meta de orçamento');
    expect(texto('.info .v')).toBe('Não definida');
    expect(texto('.info .acao')).toBe('Definir');
  });

  it('com meta definida a linha mostra valor e cadência', () => {
    montar();
    carregar({ meta: META });

    expect(texto('.info .l')).toBe('Meta de orçamento mensal');
    expect(texto('.info .v')).toBe('R$ 450,00 por mês');
    expect(texto('.info .acao')).toBe('Editar');
  });

  it('grava a meta pelo formulário e recarrega os números', () => {
    montar();
    carregar({ meta: null });

    tela().querySelector<HTMLButtonElement>('.info')!.click();
    fixture.detectChanges();
    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();

    // Valor zerado nem chega no servidor, a validacao barra antes.
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
    gravacao.flush(META);
    fixture.detectChanges();

    carregar({ meta: META });
    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(texto('.info .v')).toBe('R$ 450,00 por mês');
  });

  it('edição abre preenchida e a remoção passa por confirmação', () => {
    montar();
    carregar({ meta: META });

    tela().querySelector<HTMLButtonElement>('.info')!.click();
    fixture.detectChanges();
    expect(tela().querySelector<HTMLInputElement>('#meta-valor')!.value).toBe('450');
    expect(tela().querySelector<HTMLSelectElement>('#meta-periodo')!.value).toBe('MENSAL');

    tela().querySelector<HTMLButtonElement>('.btn-soft.destrutivo')!.click();
    fixture.detectChanges();
    // Tem um passo de confirmacao antes do DELETE, porque remover a meta apaga o progresso.
    http.expectNone({ method: 'DELETE', url: URL_META });

    tela().querySelector<HTMLButtonElement>('.btn.destrutivo')!.click();
    fixture.detectChanges();
    http.expectOne({ method: 'DELETE', url: URL_META }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    carregar({ meta: null });
    expect(texto('.info .v')).toBe('Não definida');
  });

  it('alertas de variação mostram o que subiu, não um interruptor', () => {
    montar();
    carregar({
      painel: {
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
      },
    });

    const linhas = tela().querySelectorAll('.info');
    expect(linhas[1].querySelector('.v')?.textContent?.trim()).toBe('1 item subiu de preço');
    expect(linhas[1].getAttribute('href')).toBe('/dashboard');
  });

  it('mostra "Membro desde" a partir da abertura da conta', () => {
    montar({ criadoEm: '2026-01-15T10:30:00Z' });
    carregar();

    const membro = Array.from(tela().querySelectorAll('.info')).find(
      (linha) => linha.querySelector('.l')?.textContent?.trim() === 'Membro desde'
    );
    expect(membro?.querySelector('.v')?.textContent?.trim()).toBe('Janeiro de 2026');
  });

  it('token sem criadoEm omite a linha em vez de inventar uma data', () => {
    montar({});
    carregar();

    const rotulos = Array.from(tela().querySelectorAll('.info .l'), (n) => n.textContent?.trim());
    expect(rotulos).not.toContain('Membro desde');
  });

  it('falha de carga oferece tentar de novo sem derrubar a identidade', () => {
    montar();

    http.expectOne({ method: 'GET', url: URL_REFEICOES }).error(new ProgressEvent('erro'), { status: 500 });
    // As outras chamadas do forkJoin ficam pendentes ate serem canceladas.
    http.match({ url: URL_ITENS });
    http.match((requisicao) => requisicao.url === URL_DASHBOARD);
    http.match({ url: URL_META });
    fixture.detectChanges();

    expect(texto('.empty h4')).toBe('Não deu para carregar');
    // O cabecalho nao depende da API, entao continua aparecendo.
    expect(texto('.prof h1')).toBe('Maria da Silva');

    tela().querySelector<HTMLButtonElement>('.empty .btn-out')!.click();
    fixture.detectChanges();
    carregar();

    expect(texto('.stats-cap')).toBe('Média dos últimos 30 dias · 18 de 30 dias com registro');
  });

  /** Resposta da rota /perfil, com token novo, porque o nome e o e-mail ficam dentro do JWT. */
  function tokenResposta(nome: string, email: string) {
    return {
      token: tokenDe({ nome, email, criadoEm: '2026-01-15T10:30:00Z' }),
      tipo: 'Bearer',
      expiraEmSegundos: 28800,
      usuario: { id: 1, nome, email, criadoEm: '2026-01-15T10:30:00Z' },
    };
  }

  function abrirSheet(seletor: string): void {
    tela().querySelector<HTMLButtonElement>(seletor)!.click();
    fixture.detectChanges();
  }

  function enviarFormulario(): void {
    tela().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('edita nome e e-mail, e o cabeçalho passa a refletir a sessão nova', () => {
    montar();
    carregar();

    abrirSheet('.prof .btn-out');
    // Ja abre preenchido com o que o token traz, em vez de campos vazios.
    expect(tela().querySelector<HTMLInputElement>('#perfil-nome')!.value).toBe('Maria da Silva');
    expect(tela().querySelector<HTMLInputElement>('#perfil-email')!.value).toBe('maria@email.com');

    fixture.componentInstance['formPerfil'].patchValue({
      nome: 'Maria Souza',
      email: 'maria.souza@email.com',
    });
    enviarFormulario();

    const gravacao = http.expectOne({ method: 'PUT', url: URL_PERFIL });
    expect(gravacao.request.body).toEqual({
      nome: 'Maria Souza',
      email: 'maria.souza@email.com',
    });
    const resposta = tokenResposta('Maria Souza', 'maria.souza@email.com');
    gravacao.flush(resposta);
    fixture.detectChanges();

    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
    expect(texto('.prof h1')).toBe('Maria Souza');
    expect(texto('.prof .email')).toBe('maria.souza@email.com');
    // O token salvo tambem e trocado. Sem isso, um F5 voltaria para o nome antigo.
    expect(localStorage.getItem(CHAVE_TOKEN)).toBe(resposta.token);
  });

  it('e-mail inválido é barrado antes de chegar ao servidor', () => {
    montar();
    carregar();

    abrirSheet('.prof .btn-out');
    fixture.componentInstance['formPerfil'].patchValue({ email: 'nao-e-email' });
    enviarFormulario();

    http.expectNone({ method: 'PUT', url: URL_PERFIL });
    expect(texto('.erro-campo')).toBe('Informe um e-mail válido.');
  });

  it('e-mail já usado por outra conta aparece no campo, e a sessão não muda', () => {
    montar();
    carregar();

    abrirSheet('.prof .btn-out');
    fixture.componentInstance['formPerfil'].patchValue({ email: 'outro@email.com' });
    enviarFormulario();

    http.expectOne({ method: 'PUT', url: URL_PERFIL }).flush(
      { mensagem: 'E-mail já cadastrado.', campos: { email: 'E-mail já cadastrado.' } },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    // O painel continua aberto para o usuario corrigir, e o cabecalho fica como estava.
    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();
    expect(texto('.erro-campo')).toBe('E-mail já cadastrado.');
    expect(texto('.prof .email')).toBe('maria@email.com');
  });

  it('altera a senha enviando a atual junto', () => {
    montar();
    carregar();

    const linhaSenha = Array.from(tela().querySelectorAll<HTMLButtonElement>('button.info')).find(
      (linha) => linha.querySelector('.l')?.textContent?.trim() === 'Senha'
    )!;
    linhaSenha.click();
    fixture.detectChanges();

    fixture.componentInstance['formSenha'].patchValue({
      senhaAtual: 'senha-forte-123',
      novaSenha: 'outra-senha-456',
    });
    enviarFormulario();

    const gravacao = http.expectOne({ method: 'PUT', url: URL_SENHA });
    expect(gravacao.request.body).toEqual({
      senhaAtual: 'senha-forte-123',
      novaSenha: 'outra-senha-456',
    });
    gravacao.flush(tokenResposta('Maria da Silva', 'maria@email.com'));
    fixture.detectChanges();

    expect(tela().querySelector('app-bottom-sheet')).toBeNull();
  });

  it('senha nova curta demais não chega ao servidor', () => {
    montar();
    carregar();

    fixture.componentInstance['abrirSenha']();
    fixture.detectChanges();

    fixture.componentInstance['formSenha'].patchValue({
      senhaAtual: 'senha-forte-123',
      novaSenha: '1234',
    });
    enviarFormulario();

    http.expectNone({ method: 'PUT', url: URL_SENHA });
    expect(texto('.erro-campo')).toBe('A senha deve ter entre 8 e 72 caracteres.');
  });

  it('senha atual errada mantém o sheet aberto com o aviso do servidor', () => {
    montar();
    carregar();

    fixture.componentInstance['abrirSenha']();
    fixture.detectChanges();

    fixture.componentInstance['formSenha'].patchValue({
      senhaAtual: 'errada',
      novaSenha: 'outra-senha-456',
    });
    enviarFormulario();

    http
      .expectOne({ method: 'PUT', url: URL_SENHA })
      .flush({ mensagem: 'Senha atual incorreta.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(tela().querySelector('app-bottom-sheet')).not.toBeNull();
    expect(texto('.erro-form')).toBe('Senha atual incorreta.');
    // Volta 400 e nao 401, porque a sessao nao pode cair so por errar a senha atual.
    expect(TestBed.inject(AuthService).autenticado()).toBeTrue();
  });

  it('sair encerra a sessão e apaga o token', () => {
    montar();
    carregar();

    tela().querySelector<HTMLButtonElement>('.logout')!.click();

    expect(TestBed.inject(AuthService).autenticado()).toBeFalse();
    expect(localStorage.getItem(CHAVE_TOKEN)).toBeNull();
  });
});
