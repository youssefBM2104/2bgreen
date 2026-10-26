import { rolesFromPayload } from './roles';

describe('rolesFromPayload', () => {
  it('keeps only the 2bGreen roles of realm_access.roles', () => {
    const payload = {
      realm_access: { roles: ['offline_access', 'devops', 'default-roles-2bgreen', 'citizen'] },
    };
    expect(rolesFromPayload(payload)).toEqual(['citizen', 'devops']);
  });

  it.each([
    [null],
    [undefined],
    [{}],
    [{ realm_access: {} }],
    [{ realm_access: { roles: 'devops' } }],
  ])('returns no role for a payload without usable roles (%j)', (payload) => {
    expect(rolesFromPayload(payload)).toEqual([]);
  });
});
