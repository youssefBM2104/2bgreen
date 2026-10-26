import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-access-denied',
  imports: [RouterLink],
  template: `
    <h1>Accès refusé</h1>
    <p>
      Votre compte n'a pas le rôle nécessaire pour cet espace.
      <a routerLink="/">Retour à l'accueil</a>
    </p>
  `,
})
export class AccessDenied {}
