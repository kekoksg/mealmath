import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { camposComErro, mensagemDeErro } from '../../core/http/erro-api';
import { Icone } from '../../shared/icone/icone';

type Campo = 'email' | 'senha';

/** Tela de login (RF002). */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, Icone],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});
  protected readonly mostrarSenha = signal(false);

  protected alternarSenha(): void {
    this.mostrarSenha.update((mostrar) => !mostrar);
  }

  // So required. A rota /auth/login nao valida formato de e-mail nem tamanho de senha,
  // e travar aqui impediria o login de uma conta que ja existe.
  protected readonly form = this.fb.nonNullable.group({
    email: ['', Validators.required],
    senha: ['', Validators.required],
  });

  protected entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.erro.set(null);
    this.errosServidor.set({});

    this.auth.entrar(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl(this.destino()),
      error: (falha: HttpErrorResponse) => {
        this.enviando.set(false);
        this.errosServidor.set(camposComErro(falha));
        this.erro.set(mensagemDeErro(falha, 'E-mail ou senha inválidos.'));
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
    return campo === 'email' ? 'Informe seu e-mail.' : 'Informe sua senha.';
  }

  /**
   * Para onde ir depois de logar. O parametro retorno vem da URL, entao so aceita caminho
   * interno. Uma URL absoluta ou comecando com // levaria o usuario para fora do app.
   */
  private destino(): string {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    if (retorno && retorno.startsWith('/') && !retorno.startsWith('//')) {
      return retorno;
    }
    return '/dashboard';
  }
}
