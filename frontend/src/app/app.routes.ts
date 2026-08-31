import { Routes } from '@angular/router';
import { authGuard, convidadoGuard } from './core/auth/auth.guard';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [convidadoGuard],
    title: 'Entrar · MealMath',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'cadastro',
    canActivate: [convidadoGuard],
    title: 'Criar conta · MealMath',
    loadComponent: () => import('./features/cadastro/cadastro').then((m) => m.Cadastro),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Visão Geral · MealMath',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      // Refeicoes e Diario sao o mesmo componente em duas secoes, e o data.aba diz qual e.
      // Cada uma tem a sua URL porque cada uma e um item do menu, e e a URL que decide qual
      // deles fica destacado.
      {
        path: 'refeicoes',
        data: { aba: 'biblioteca' },
        title: 'Refeições · MealMath',
        loadComponent: () => import('./features/dieta/dieta').then((m) => m.Dieta),
      },
      {
        path: 'diario',
        data: { aba: 'diario' },
        title: 'Diário · MealMath',
        loadComponent: () => import('./features/dieta/dieta').then((m) => m.Dieta),
      },
      // Endereco antigo da tela, de quando ela se chamava Dieta.
      { path: 'dieta', pathMatch: 'full', redirectTo: 'refeicoes' },
      {
        path: 'mercado',
        title: 'Mercado · MealMath',
        loadComponent: () => import('./features/mercado/mercado').then((m) => m.Mercado),
      },
      {
        path: 'perfil',
        title: 'Perfil · MealMath',
        loadComponent: () => import('./features/perfil/perfil').then((m) => m.Perfil),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
