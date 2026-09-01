import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { mesEAnoDe } from '../../core/dominio/data';
import { positivo } from '../../core/dominio/validadores';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { BottomSheet } from '../../shared/bottom-sheet/bottom-sheet';
import { MoedaPipe, formatarMoeda } from '../../shared/moeda.pipe';
import { MetaOrcamentoResponse, PeriodoMeta } from '../dashboard/dashboard.model';
import { DashboardService } from '../dashboard/dashboard.service';
import { MetaOrcamentoService } from '../dashboard/meta-orcamento.service';
import { RefeicaoService } from '../dieta/refeicao.service';
import { ItemMercadoService } from '../mercado/item-mercado.service';
import { Icone } from '../../shared/icone/icone';
import { PerfilService } from './perfil.service';

const PERIODOS_META: readonly { valor: PeriodoMeta; rotulo: string }[] = [
  { valor: 'MENSAL', rotulo: 'Por mês' },
  { valor: 'SEMANAL', rotulo: 'Por semana' },
];

/** Adjetivo da meta, para montar a frase "Meta de orcamento mensal". */
const ADJETIVO_META: Readonly<Record<PeriodoMeta, string>> = {
  SEMANAL: 'semanal',
  MENSAL: 'mensal',
};

/** Complemento do valor, para montar "R$ 450,00 por mes". */
const CADENCIA_META: Readonly<Record<PeriodoMeta, string>> = {
  SEMANAL: 'por semana',
  MENSAL: 'por mês',
};

const MENSAGENS_OBRIGATORIO: Readonly<Record<string, string>> = {
  nome: 'Informe seu nome.',
  email: 'Informe seu e-mail.',
  senhaAtual: 'Informe sua senha atual.',
  novaSenha: 'Informe a nova senha.',
};

const LIMITES: Readonly<Record<string, string>> = {
  nome: 'O nome deve ter no máximo 120 caracteres.',
  email: 'O e-mail deve ter no máximo 180 caracteres.',
  novaSenha: 'A senha deve ter entre 8 e 72 caracteres.',
};

/** Passos do painel de baixo. Quando esta null, o painel esta fechado. */
type Etapa = 'meta' | 'remover-meta' | 'editar' | 'senha';

/** Numeros da conta, montados a partir de quatro chamadas separadas. */
interface ResumoPerfil {
  readonly refeicoes: number;
  readonly itens: number;
  /** Media sobre os 30 dias da janela, e nao sobre os dias que tem registro. */
  readonly custoMedioPorDia: number;
  readonly diasComRegistro: number;
  readonly totalDeDias: number;
  readonly altasDePreco: number;
  /** Fica null quando nao tem meta definida, porque a API responde 204 (RF009). */
  readonly meta: MetaOrcamentoResponse | null;
}

/**
 * Tela da conta do usuario e da meta de orcamento (RF009).
 *
 * Nome e e-mail saem do token e aparecem na hora. Os numeros dependem da API e ficam num bloco
 * separado, com carregamento e erro proprios, para a tela nunca ficar em branco.
 */
@Component({
  selector: 'app-perfil',
  imports: [RouterLink, ReactiveFormsModule, BottomSheet, MoedaPipe, Icone],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Perfil {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly refeicoes = inject(RefeicaoService);
  private readonly mercado = inject(ItemMercadoService);
  private readonly dashboards = inject(DashboardService);
  private readonly metas = inject(MetaOrcamentoService);
  private readonly perfis = inject(PerfilService);

  protected readonly periodosMeta = PERIODOS_META;

  // Nao preciso chamar a API: esses dados ja estao no token, e a tela nao deve piscar para
  // mostrar o proprio nome do usuario.
  protected readonly nome = computed(() => this.auth.usuario()?.nome?.trim() || 'Sua conta');
  protected readonly email = computed(() => this.auth.usuario()?.email ?? '');

  /** Fica null em token antigo, que nao tem esse campo. A linha some em vez de mostrar errado. */
  protected readonly membroDesde = computed(() => mesEAnoDe(this.auth.usuario()?.criadoEm));

  protected readonly dados = signal<ResumoPerfil | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  constructor() {
    this.carregar();
  }

    /**
     * As quatro chamadas sao paralelas porque nenhuma depende da outra, e o bloco inteiro cai se uma
     * falhar: metade dos numeros preenchida confunde mais do que ajuda.
     */
  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    forkJoin({
      refeicoes: this.refeicoes.listar(),
      itens: this.mercado.listar(),
      painel: this.dashboards.consolidar('MES'),
      meta: this.metas.buscar(),
    }).subscribe({
      next: ({ refeicoes, itens, painel, meta }) => {
        this.dados.set({
          refeicoes: refeicoes.length,
          itens: itens.length,
          custoMedioPorDia: painel.custoMedioPorDia,
          diasComRegistro: painel.completude.diasComRegistro,
          totalDeDias: painel.completude.totalDeDias,
          altasDePreco: painel.altasDePreco.length,
          meta,
        });
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        this.erro.set(mensagemDeErro(falha, 'Não foi possível carregar os dados da conta.'));
      },
    });
  }

  /** Conta que ainda nao tem nada cadastrado. Mostro um convite em vez de tres zeros. */
  protected readonly contaVazia = computed(() => {
    const resumo = this.dados();
    return resumo !== null && resumo.itens === 0 && resumo.refeicoes === 0;
  });

  /**
   * Monta "18 de 30 dias com registro". Como a media divide pela janela inteira, sem esse
   * contador um mes com tres dias anotados pareceria um mes inteiro muito barato.
   */
  protected readonly completude = computed(() => {
    const resumo = this.dados();
    if (!resumo) {
      return '';
    }
    const dias = resumo.totalDeDias === 1 ? 'dia' : 'dias';
    return `${resumo.diasComRegistro} de ${resumo.totalDeDias} ${dias} com registro`;
  });

  /** Vira "Meta de orcamento mensal" quando esta definida, e fica sem adjetivo quando nao esta. */
  protected readonly rotuloDaMeta = computed(() => {
    const meta = this.dados()?.meta;
    return meta ? `Meta de orçamento ${ADJETIVO_META[meta.periodo]}` : 'Meta de orçamento';
  });

  protected readonly valorDaMeta = computed(() => {
    const meta = this.dados()?.meta;
    return meta ? `${formatarMoeda(meta.valor)} ${CADENCIA_META[meta.periodo]}` : 'Não definida';
  });

  /**
   * No prototipo essa linha dizia "Ativado", como se tivesse um botao de ligar. Nao existe
   * configuracao de notificacao no sistema, entao a linha mostra o que existe de verdade: quantos
   * itens subiram de preco desde a ultima compra (RF007).
   */
  protected readonly resumoDeAltas = computed(() => {
    const total = this.dados()?.altasDePreco ?? 0;
    if (total === 0) {
      return 'Nenhuma alta desde a última compra';
    }
    return total === 1 ? '1 item subiu de preço' : `${total} itens subiram de preço`;
  });

  protected readonly etapa = signal<Etapa | null>(null);
  protected readonly salvando = signal(false);
  protected readonly removendo = signal(false);
  protected readonly erroForm = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});

  protected readonly formMeta = this.fb.group({
    valor: this.fb.control<number | null>(null, [Validators.required, positivo]),
    periodo: this.fb.nonNullable.control<PeriodoMeta>('MENSAL', Validators.required),
  });

  protected readonly formPerfil = this.fb.group({
    nome: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(120)]),
    email: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.email,
      Validators.maxLength(180),
    ]),
  });

  protected readonly formSenha = this.fb.group({
    senhaAtual: this.fb.nonNullable.control('', Validators.required),
    novaSenha: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.minLength(8),
      Validators.maxLength(72),
    ]),
  });

  private static readonly TITULOS: Readonly<Record<Etapa, string>> = {
    meta: 'Meta de orçamento',
    'remover-meta': 'Remover meta',
    editar: 'Editar perfil',
    senha: 'Alterar senha',
  };

  protected readonly tituloSheet = computed(() => {
    const passo = this.etapa();
    return passo ? Perfil.TITULOS[passo] : '';
  });

  protected abrirMeta(): void {
    const meta = this.dados()?.meta;
    this.formMeta.reset({ valor: meta?.valor ?? null, periodo: meta?.periodo ?? 'MENSAL' });
    this.erroForm.set(null);
    this.errosServidor.set({});
    this.salvando.set(false);
    this.removendo.set(false);
    this.etapa.set('meta');
  }

  /** Os campos ja abrem preenchidos com o que vem do token, sem esperar a API. */
  protected abrirEdicao(): void {
    const usuario = this.auth.usuario();
    this.formPerfil.reset({ nome: usuario?.nome ?? '', email: usuario?.email ?? '' });
    this.limparEstadoDoForm();
    this.etapa.set('editar');
  }

  protected abrirSenha(): void {
    this.formSenha.reset({ senhaAtual: '', novaSenha: '' });
    this.limparEstadoDoForm();
    this.etapa.set('senha');
  }

  private limparEstadoDoForm(): void {
    this.erroForm.set(null);
    this.errosServidor.set({});
    this.salvando.set(false);
    this.removendo.set(false);
  }

  protected salvarPerfil(): void {
    if (this.formPerfil.invalid) {
      this.formPerfil.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.erroForm.set(null);
    this.errosServidor.set({});

    this.perfis.atualizar(this.formPerfil.getRawValue()).subscribe({
      // Os numeros nao sao recarregados porque nome e e-mail nao entram em nenhum deles. O servico
      // ja reabriu a sessao, e o cabecalho se atualiza sozinho pelo signal do AuthService.
      next: () => this.fecharSheet(),
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível salvar seus dados.'));
      },
    });
  }

  protected salvarSenha(): void {
    if (this.formSenha.invalid) {
      this.formSenha.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.erroForm.set(null);
    this.errosServidor.set({});

    this.perfis.alterarSenha(this.formSenha.getRawValue()).subscribe({
      next: () => this.fecharSheet(),
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível alterar a senha.'));
      },
    });
  }

  protected invalido(controle: AbstractControl, campo: string): boolean {
    return (controle.touched && controle.invalid) || campo in this.errosServidor();
  }

  /** O erro do servidor vem primeiro, porque so ele sabe se o e-mail ja esta cadastrado. */
  protected mensagemDoCampo(controle: AbstractControl, campo: string): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('required')) {
      return MENSAGENS_OBRIGATORIO[campo] ?? 'Campo obrigatório.';
    }
    if (controle.hasError('email')) {
      return 'Informe um e-mail válido.';
    }
    if (controle.hasError('minlength') || controle.hasError('maxlength')) {
      return LIMITES[campo] ?? 'Tamanho inválido.';
    }
    return 'Valor inválido.';
  }

  protected confirmarRemocao(): void {
    this.erroForm.set(null);
    this.etapa.set('remover-meta');
  }

  protected voltarParaMeta(): void {
    this.erroForm.set(null);
    this.etapa.set('meta');
  }

  protected fecharSheet(): void {
    this.etapa.set(null);
  }

  protected salvarMeta(): void {
    if (this.formMeta.invalid) {
      this.formMeta.markAllAsTouched();
      return;
    }

    const { valor, periodo } = this.formMeta.getRawValue();

    this.salvando.set(true);
    this.erroForm.set(null);
    this.errosServidor.set({});

    this.metas.definir({ valor: valor!, periodo }).subscribe({
      next: () => {
        this.fecharSheet();
        // Recarrega tudo porque mudar a meta muda o progresso que o dashboard mostra.
        // Corrigindo so a linha da tela, os dois numeros ficariam diferentes.
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível salvar a meta.'));
      },
    });
  }

  /** Sem meta o dashboard volta a oferecer o botao de definir. Nenhum registro e perdido. */
  protected removerMeta(): void {
    this.removendo.set(true);
    this.erroForm.set(null);

    this.metas.remover().subscribe({
      next: () => {
        this.fecharSheet();
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.removendo.set(false);
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível remover a meta.'));
      },
    });
  }

  protected invalidoValor(): boolean {
    const controle = this.formMeta.controls.valor;
    return (controle.touched && controle.invalid) || 'valor' in this.errosServidor();
  }

  protected mensagemValor(): string | null {
    const doServidor = this.errosServidor()['valor'];
    if (doServidor) {
      return doServidor;
    }

    const controle = this.formMeta.controls.valor;
    if (!controle.touched || controle.valid) {
      return null;
    }
    return controle.hasError('positivo')
      ? 'O valor da meta deve ser maior que zero.'
      : 'Informe o valor-limite da meta.';
  }

  protected sair(): void {
    this.auth.encerrarSessao();
    this.router.navigate(['/login']);
  }
}
