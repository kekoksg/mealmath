import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CATEGORIAS, Categoria, iconeDaCategoria } from '../../core/dominio/categoria';
import { normalizar } from '../../core/dominio/texto';
import {
  CustoDeReferencia,
  UNIDADES,
  UnidadeMedida,
  custoDeReferencia,
  paraBase,
  rotuloDaUnidade,
  unidadeBaseDe,
} from '../../core/dominio/unidade';
import { positivo } from '../../core/dominio/validadores';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { BottomSheet } from '../../shared/bottom-sheet/bottom-sheet';
import { MoedaPipe, formatarMoeda } from '../../shared/moeda.pipe';
import { ItemMercadoRequest, ItemMercadoResponse } from './item-mercado.model';
import { ItemMercadoService } from './item-mercado.service';
import { Icone } from '../../shared/icone/icone';

type Campo = 'nome' | 'preco' | 'quantidadeEmbalagem' | 'unidade' | 'categoria';

const QUANTIDADE = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });

/** Tela do RF004, de cadastro de item de mercado, e do RF007, de atualizacao de preco. */
@Component({
  selector: 'app-mercado',
  imports: [ReactiveFormsModule, BottomSheet, MoedaPipe, Icone],
  templateUrl: './mercado.html',
  styleUrl: './mercado.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Mercado {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ItemMercadoService);

  protected readonly categorias = CATEGORIAS;
  protected readonly unidades = UNIDADES;

  protected readonly itens = signal<ItemMercadoResponse[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly busca = signal('');

  protected readonly filtrados = computed(() => {
    const termo = normalizar(this.busca());
    if (!termo) {
      return this.itens();
    }
    return this.itens().filter((item) => normalizar(item.nome).includes(termo));
  });

  protected readonly resumo = computed(() => {
    const total = this.itens().length;
    return total === 1 ? '1 item cadastrado' : `${total} itens cadastrados`;
  });

  protected readonly sheetAberta = signal(false);
  protected readonly emEdicao = signal<ItemMercadoResponse | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroForm = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});
  /** Precos antigos do item que esta sendo editado, do mais novo para o mais antigo. */
  private readonly historico = signal<number[]>([]);

  protected readonly form = this.fb.group({
    nome: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(120)]),
    preco: this.fb.control<number | null>(null, [Validators.required, positivo]),
    quantidadeEmbalagem: this.fb.control<number | null>(null, [Validators.required, positivo]),
    unidade: this.fb.nonNullable.control<UnidadeMedida>('KG', Validators.required),
    categoria: this.fb.nonNullable.control<Categoria>('OUTROS', Validators.required),
  });

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

    /**
     * Previa recalculada a cada tecla, sem chamar o servidor. Fica null enquanto preco ou quantidade
     * nao forem positivos: R$ 0,00 num formulario incompleto esconderia o custo real do mesmo jeito.
     */
  protected readonly previa = computed<CustoDeReferencia | null>(() => {
    const { preco, quantidadeEmbalagem, unidade } = this.valores();
    if (!preco || !quantidadeEmbalagem || preco <= 0 || quantidadeEmbalagem <= 0) {
      return null;
    }

    const escolhida = unidade ?? 'KG';
    const custoUnitario = preco / paraBase(quantidadeEmbalagem, escolhida);
    return custoDeReferencia(custoUnitario, unidadeBaseDe(escolhida));
  });

  /** Monta "R$ 16,50 -> R$ 18,90", do preco mais antigo ate o que vale hoje. */
  protected readonly historicoTexto = computed(() => {
    const item = this.emEdicao();
    const anteriores = this.historico();
    if (!item || anteriores.length === 0) {
      return null;
    }
    return [...anteriores].reverse().concat(item.preco).map(formatarMoeda).join(' → ');
  });

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.api.listar().subscribe({
      next: (itens) => {
        this.itens.set(itens);
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.carregando.set(false);
        this.erro.set(mensagemDeErro(falha, 'Não foi possível carregar os itens de mercado.'));
      },
    });
  }

  protected aoBuscar(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
  }

  protected limparBusca(): void {
    this.busca.set('');
  }

  protected abrirNovo(): void {
    this.prepararSheet(null);
    this.form.reset({
      nome: '',
      preco: null,
      quantidadeEmbalagem: null,
      unidade: 'KG',
      categoria: 'OUTROS',
    });
  }

  protected abrirEdicao(item: ItemMercadoResponse): void {
    this.prepararSheet(item);
    this.form.reset({
      nome: item.nome,
      preco: item.preco,
      quantidadeEmbalagem: item.quantidadeEmbalagem,
      unidade: item.unidade,
      categoria: item.categoria,
    });

    // Se o historico falhar, o usuario ainda consegue editar o item. O bloco so nao aparece.
    this.api.historico(item.id).subscribe({
      next: (registros) => this.historico.set(registros.map((registro) => registro.preco)),
      error: () => this.historico.set([]),
    });
  }

  protected fecharSheet(): void {
    this.sheetAberta.set(false);
    this.emEdicao.set(null);
  }

  private prepararSheet(item: ItemMercadoResponse | null): void {
    this.emEdicao.set(item);
    this.historico.set([]);
    this.erroForm.set(null);
    this.errosServidor.set({});
    this.salvando.set(false);
    this.sheetAberta.set(true);
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const bruto = this.form.getRawValue();
    const requisicao: ItemMercadoRequest = {
      nome: bruto.nome.trim(),
      preco: bruto.preco!,
      quantidadeEmbalagem: bruto.quantidadeEmbalagem!,
      unidade: bruto.unidade,
      categoria: bruto.categoria,
    };

    this.salvando.set(true);
    this.erroForm.set(null);
    this.errosServidor.set({});

    const item = this.emEdicao();
    const gravacao = item ? this.api.atualizar(item.id, requisicao) : this.api.criar(requisicao);

    gravacao.subscribe({
      next: () => {
        this.fecharSheet();
        // Recarrega a lista em vez de corrigir na mao, porque a ordenacao e por nome e o
        // custo unitario do item salvo vem recalculado do servidor.
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.salvando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erroForm.set(mensagemDeErro(falha, 'Não foi possível salvar o item.'));
      },
    });
  }

  protected invalido(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return (controle.touched && controle.invalid) || campo in this.errosServidor();
  }

  protected mensagem(campo: Campo): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }

    const controle = this.form.controls[campo];
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('positivo')) {
      return campo === 'preco'
        ? 'O preço deve ser maior que zero.'
        : 'A quantidade deve ser maior que zero.';
    }
    if (controle.hasError('maxlength')) {
      return 'O nome deve ter no máximo 120 caracteres.';
    }
    return AVISOS[campo];
  }

  /** Custo unitario de um item da lista, na medida que a tela mostra: 100 g, 100 mL ou 1 un. */
  protected referencia(item: ItemMercadoResponse): CustoDeReferencia {
    return custoDeReferencia(item.custoUnitario, item.unidadeBase);
  }

  /** Monta "1 kg", "0,5 L", "12 un". */
  protected embalagem(item: ItemMercadoResponse): string {
    return `${QUANTIDADE.format(item.quantidadeEmbalagem)} ${rotuloDaUnidade(item.unidade)}`;
  }

  protected icone(categoria: Categoria): string {
    return iconeDaCategoria(categoria);
  }
}

const AVISOS: Record<Campo, string> = {
  nome: 'Informe o nome do item.',
  preco: 'Informe o preço pago.',
  quantidadeEmbalagem: 'Informe a quantidade da embalagem.',
  unidade: 'Escolha a unidade da embalagem.',
  categoria: 'Escolha a categoria.',
};
