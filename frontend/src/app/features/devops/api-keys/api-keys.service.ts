import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../../../core/auth/auth.config';

/** Une clé d'API active, telle que platform la montre : jamais la clé elle-même. */
export interface ApiKey {
  id: number;
  name: string;
  /** Début de la clé, par exemple `2bg_ab12`. */
  prefix: string;
  createdAt: string;
  /** `null` tant que la clé n'a servi à aucun appel accepté. */
  lastUsedAt: string | null;
  /** Appels acceptés depuis 00:00 UTC. */
  callsToday: number;
  dailyQuota: number;
}

/** La réponse à la création : la seule fois où la clé complète est donnée. */
export interface CreatedApiKey extends ApiKey {
  key: string;
}

export const MAX_KEYS = 5;
export const MAX_NAME_LENGTH = 60;

const URL = `${PLATFORM_URL}/api/v1/automation/api-keys`;

/** Les clés de l'utilisateur connecté : platform lit son identité dans le token, jamais dans la requête. */
@Injectable({ providedIn: 'root' })
export class ApiKeysService {
  private readonly http = inject(HttpClient);

  list(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(URL);
  }

  create(name: string): Observable<CreatedApiKey> {
    return this.http.post<CreatedApiKey>(URL, { name });
  }

  revoke(key: ApiKey): Observable<void> {
    return this.http.delete<void>(`${URL}/${key.id}`);
  }
}
