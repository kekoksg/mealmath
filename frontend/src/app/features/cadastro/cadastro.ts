import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { Icone } from '../../shared/icone/icone';

type Campo = 'nome' | 'email' | 'senha';

/** As mensagens sao as mesmas validacoes do CadastroRequest no backend. */
const AVISOS: Record<Campo, Record<string, string>> = {
  nome: {
    required: 'Informe seu nome.',
    maxlength: 'O nome deve ter no máximo 120 caracteres.',
  },
  email: {
    required: 'Informe seu e-mail.',
    email: 'Informe um e-mail válido.',
    maxlength: 'O e-mail deve ter no máximo 180 caracteres.',
  },
  senha: {
    required: 'Crie uma senha.',
    minlength: 'A senha deve ter ao menos 8 caracteres.',
    maxlength: 'A senha deve ter no máximo 72 caracteres.',
  },
};

/** Tela de cadastro (RF001). A API ja devolve o usuario logado, sem precisar passar pelo login. */
@Component({
  selector: 'app-cadastro',
  imports: [ReactiveFormsModule, RouterLink, Icone],
  templateUrl: './cadastro.html',
  styleUrl: './cadastro.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Cadastro {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});
  protected readonly mostrarSenha = signal(false);

  protected alternarSenha(): void {
    this.mostrarSenha.update((mostrar) => !mostrar);
  }

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    // O 72 e o limite do bcrypt e nao uma escolha de layout.
    senha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected criarConta(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.erro.set(null);
    this.errosServidor.set({});

    this.auth.registrar(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (falha: HttpErrorResponse) => {
        this.enviando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erro.set(mensagemDeErro(falha, 'Não foi possível criar a conta. Tente de novo.'));
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

    const avisos = AVISOS[campo];
    const erro = Object.keys(controle.errors ?? {}).find((chave) => chave in avisos);
    return erro ? avisos[erro] : 'Verifique este campo.';
  }
}
