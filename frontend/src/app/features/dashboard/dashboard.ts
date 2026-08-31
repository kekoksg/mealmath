import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { iconeDaCategoria } from '../../core/dominio/categoria';
import { custoDeReferencia } from '../../core/dominio/unidade';
import { positivo } from '../../core/dominio/validadores';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { BottomSheet } from '../../shared/bottom-sheet/bottom-sheet';
import { MoedaPipe, formatarMoeda } from '../../shared/moeda.pipe';
import {
  AltaPrecoResponse,
  DashboardResponse,
  ItemImpactoResponse,
  PeriodoDashboard,
  PeriodoMeta,
} from './dashboard.model';
import { DashboardService } from './dashboard.service';
import { MetaOrcamentoService } from './meta-orcamento.service';
import { Icone } from '../../shared/icone/icone';

interface DefinicaoPeriodo {
  readonly valor: PeriodoDashboard;
  /** Texto que aparece no botao de periodo. */
  readonly rotulo: string;
  /** Texto em maiusculo do topo da tela, tipo "CUSTO TOTAL - SEMANA". */
  readonly caixaAlta: string;
  /** Nome do periodo anterior, usado na frase da variacao. */
  readonly anterior: string;
  /** Fecha a frase do saldo: "R$ 89,80 ainda disponivel" mais esse sufixo. */
  readonly sufixo: string;
}

const PERIODOS: readonly DefinicaoPeriodo[] = [
  { valor: 'DIA', rotulo: 'Hoje', caixaAlta: 'Hoje', anterior: 'ontem', sufixo: 'hoje' },
  {
    valor: 'SEMANA',
    rotulo: 'Semana',
    caixaAlta: 'Semana',
    anterior: 'semana passada',
    sufixo: 'na semana',
  },
  { valor: 'MES', rotulo: 'Mês', caixaAlta: 'Mês', anterior: 'mês passado', sufixo: 'no mês' },
];

const PERIODOS_META: readonly { valor: PeriodoMeta; rotulo: string }[] = [
  { valor: 'MENSAL', rotulo: 'Por mês' },
  { valor: 'SEMANAL', rotulo: 'Por semana' },
];

const PERCENTUAL = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });

/**
 * Variacao menor que isso e ruido. Arredondada para 0 casas ela viraria "+0%" com seta de
 * alta, o que nao faz sentido. Abaixo desse valor a seta fica neutra.
 */
const RUIDO_PERCENTUAL = 0.5;

/** Passando dessa fatia da meta a barra vira amarela: ainda esta dentro, mas apertado. */
const ZONA_DE_RISCO = 80;

/** Variacao mostrada no topo da tela. */
interface Variacao {
  readonly seta: string;
  readonly texto: string;
  /**
   * Gastou mais que no periodo anterior. Num app de custo subir e ruim, entao a tag troca
   * de cor: sem isso a alta saia no mesmo tom da queda e a cor dizia o contrario do dado.
   */
  readonly alta: boolean;
}

/**
 * Tela do custo consolidado por periodo (RF006), com o progresso da meta (RF010).
 *
 * Os numeros ja chegam calculados do backend; a tela so formata, e e aqui que o arredondamento
 * acontece.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, ReactiveFormsModule, BottomSheet, MoedaPipe, Icone],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(DashboardService);
  private readonly metas = inject(MetaOrcamentoService);
  private readonly auth = inject(AuthService);

  protected readonly periodos = PERIODOS;
  protected readonly periodosMeta = PERIODOS_META;

  /** A tela abre em "Hoje", que e o gasto sobre o qual ainda da para fazer alguma coisa. */
  protected readonly periodo = signal<PeriodoDashboard>('DIA');
  protected readonly dados = signal<DashboardResponse | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

    /** So o primeiro nome, que e como a pessoa se chama. */
  protected readonly saudacao = computed(() => {
    const nome = this.auth.usuario()?.nome?.trim().split(/\s+/)[0];
    return nome ? `Olá, ${nome}` : 'Olá';
  });

  // O valor padrao e o mesmo periodo em que a tela abre. Ele so seria usado se chegasse um
  // periodo que nao esta na lista, e cair em um rotulo diferente confundiria mais que ajudar.
  private readonly definicao = computed(
    () => PERIODOS.find((item) => item.valor === this.periodo()) ?? PERIODOS[0]
  );

  protected readonly rotuloDoPeriodo = computed(() => this.definicao().caixaAlta);

  /** Liga o icone de aviso do lado da legenda da meta. */
  protected readonly metaEstourada = computed(() => this.dados()?.meta?.acimaDaMeta === true);

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.api.consolidar(this.periodo()).subscribe({
      next: (dados) => {
        this.dados.set(dados);
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        this.erro.set(mensagemDeErro(falha, 'Não foi possível carregar a visão geral.'));
      },
    });
  }

    /**
     * Nao limpo o campo dados aqui. A tela segue mostrando o periodo anterior, so que apagado pela
     * classe .atualizando, ate a resposta chegar. Limpando, o @else if do template derrubaria a tela
     * inteira e montaria tudo de novo, dando o efeito de piscar a cada troca de botao.
     */
  protected trocarPeriodo(valor: PeriodoDashboard): void {
    if (valor === this.periodo()) {
      return;
    }
    this.periodo.set(valor);
    this.carregar();
  }

  /**
   * Fica null quando o periodo anterior nao teve custo. Sem denominador a variacao nao e 0%
   * nem +100%, ela simplesmente nao existe, e o indicador some da tela.
   */
  protected readonly variacao = computed<Variacao | null>(() => {
    const comparativo = this.dados()?.comparativo;
    if (!comparativo) {
      return null;
    }

    const valor = comparativo.variacaoPercentual;
    const alta = valor > RUIDO_PERCENTUAL;
    const seta = alta ? '▲' : valor < -RUIDO_PERCENTUAL ? '▼' : '▪';
    const sinal = valor > 0 ? '+' : '';
    return {
      seta,
      texto: `${sinal}${PERCENTUAL.format(valor)}% vs. ${this.definicao().anterior}`,
      alta,
    };
  });

    /**
     * Expoe a lacuna que a media por dia esconde.
     *
     * Vazio no periodo de um dia, onde so poderia dizer 0/1 ou 1/1 — o que o custo do dia ja informa.
     * Decidido pela duracao e nao pelo valor 'DIA', para um periodo novo de um dia herdar isso sozinho.
     */
  protected readonly completude = computed(() => {
    const completude = this.dados()?.completude;
    if (!completude || completude.totalDeDias <= 1) {
      return '';
    }
    return `Dias registrados: ${completude.diasComRegistro}/${completude.totalDeDias}`;
  });

  /**
   * Itens do diario que estao sem preco vinculado. Eles ficaram de fora de todos os totais da
   * tela, e sem esse aviso o custo total estaria enganando o usuario.
   */
  protected readonly avisoSemPreco = computed(() => {
    const nomes = this.dados()?.itensSemPreco ?? [];
    if (!nomes.length) {
      return null;
    }
    const sujeito = nomes.length === 1 ? 'item ficou' : 'itens ficaram';
    return `${nomes.length} ${sujeito} fora do total por não ter preço: ${nomes.join(', ')}.`;
  });

  /** Largura da barra. Para em 100% porque a barra acabou, e o estouro aparece na legenda. */
  protected readonly larguraDaMeta = computed(() => {
    const meta = this.dados()?.meta;
    return meta ? Math.min(100, Math.max(0, meta.percentualConsumido)) : 0;
  });

  /** Verde dentro da meta, amarelo apertado e vermelho estourado. A cor vem do SCSS. */
  protected readonly faixaDaMeta = computed(() => {
    const meta = this.dados()?.meta;
    if (!meta) {
      return 'ok';
    }
    if (meta.acimaDaMeta) {
      return 'acima';
    }
    return meta.percentualConsumido > ZONA_DE_RISCO ? 'risco' : 'ok';
  });

  protected readonly legendaDaMeta = computed(() => {
    const meta = this.dados()?.meta;
    if (!meta) {
      return '';
    }
    // O sufixo e da janela que esta na tela e nao da meta. Com meta mensal vista por dia, o
    // que sobra e o saldo daquele dia. So "ainda disponivel" ficaria ambiguo.
    return meta.acimaDaMeta
      ? `${formatarMoeda(-meta.saldo)} acima da meta`
      : `${formatarMoeda(meta.saldo)} ainda disponível ${this.definicao().sufixo}`;
  });

  protected readonly sheetAberta = signal(false);
  protected readonly salvando = signal(false);
  protected readonly erroForm = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});

  protected readonly formMeta = this.fb.group({
    valor: this.fb.control<number | null>(null, [Validators.required, positivo]),
    periodo: this.fb.nonNullable.control<PeriodoMeta>('MENSAL', Validators.required),
  });

  /**
   * O progresso ja traz o valor cadastrado e o periodo dele, entao editar nao precisa
   * chamar o GET /meta-orcamento. O formulario e preenchido com o que a tela ja tem.
   */
  protected abrirMeta(): void {
    const meta = this.dados()?.meta;
    this.formMeta.reset({
      valor: meta?.valor ?? null,
      periodo: meta?.periodo ?? 'MENSAL',
    });
    this.erroForm.set(null);
    this.errosServidor.set({});
    this.salvando.set(false);
    this.sheetAberta.set(true);
  }

  protected fecharMeta(): void {
    this.sheetAberta.set(false);
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
        this.fecharMeta();
        // Recarrega tudo em vez de corrigir na mao, porque o rateio para a janela e o
        // percentual consumido sao conta do backend e nao do formulario.
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível salvar a meta.'));
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

  /** Monta "34%" ou "0%". O backend manda com 2 casas, mas na tela isso nao e preciso. */
  protected percentual(valor: number): string {
    return `${PERCENTUAL.format(valor)}%`;
  }

  protected icone(item: ItemImpactoResponse): string {
    return iconeDaCategoria(item.categoria);
  }

    /**
     * Mostra a etiqueta enquanto a embalagem nao muda, e o custo unitario quando ela muda.
     *
     * A etiqueta e o que o usuario reconhece, mas a porcentagem do lado e calculada no custo por g,
     * mL ou un. Quando o tamanho da embalagem muda, as duas passam a se contradizer: R$ 5,20 por 1 L
     * virando R$ 5,60 por 2 L e etiqueta 8% maior com custo por mL 46% menor. Ai quem tem que
     * aparecer e a base real da conta.
     */
  protected transicao(alta: AltaPrecoResponse): string {
    const anterior = custoDeReferencia(alta.custoUnitarioAnterior, alta.unidadeBase);
    const atual = custoDeReferencia(alta.custoUnitarioAtual, alta.unidadeBase);

    const razaoEtiqueta = alta.precoAtual / alta.precoAnterior;
    const razaoCusto = alta.custoUnitarioAtual / alta.custoUnitarioAnterior;

    // Folga de 0,1% porque as duas contas vem de divisoes sem arredondar.
    if (Math.abs(razaoEtiqueta - razaoCusto) < 0.001) {
      return `${formatarMoeda(alta.precoAnterior)} → ${formatarMoeda(alta.precoAtual)}`;
    }
    return `${formatarMoeda(anterior.valor)} → ${formatarMoeda(atual.valor)} ${atual.rotulo}`;
  }
}
