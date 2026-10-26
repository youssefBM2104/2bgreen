import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppRole } from '../../core/auth/roles';

@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, MatToolbarModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);

  private readonly allLinks: { path: string; label: string; role: AppRole }[] = [
    { path: '/citoyen', label: 'Citoyen', role: 'citizen' },
    { path: '/devops', label: 'DevOps', role: 'devops' },
    { path: '/elu', label: 'Élu régional', role: 'regional_official' },
  ];

  private readonly roles = toSignal(this.auth.roles(), { initialValue: [] as AppRole[] });

  /** Chacun ne voit que les espaces auxquels son rôle donne accès. */
  protected readonly links = computed(() =>
    this.allLinks.filter((link) => this.roles().includes(link.role)),
  );
}
