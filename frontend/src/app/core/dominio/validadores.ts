import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Maior que zero, e nao Validators.min(0): o zero passaria e o calculo dividiria por zero. */
export function positivo(controle: AbstractControl): ValidationErrors | null {
  const valor = controle.value as number | null;
  if (valor === null || valor === undefined) {
    return null;
  }
  return valor > 0 ? null : { positivo: true };
}
