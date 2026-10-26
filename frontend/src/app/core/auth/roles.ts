export type AppRole = 'citizen' | 'devops' | 'regional_official';

/** Ordre de priorité quand un utilisateur a plusieurs rôles (pour la page d'accueil). */
export const APP_ROLES: readonly AppRole[] = ['citizen', 'devops', 'regional_official'];

export const ROLE_HOME: Record<AppRole, string> = {
  citizen: '/citoyen',
  devops: '/devops',
  regional_official: '/elu',
};

/** Extrait les rôles 2bGreen de `realm_access.roles`, comme le fait platform (les autres rôles Keycloak sont ignorés). */
export function rolesFromPayload(payload: unknown): AppRole[] {
  const roles = (payload as { realm_access?: { roles?: unknown } } | null)?.realm_access?.roles;
  if (!Array.isArray(roles)) {
    return [];
  }
  return APP_ROLES.filter((role) => roles.includes(role));
}
