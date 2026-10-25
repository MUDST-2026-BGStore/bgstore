export interface AuthState {
  authenticated: boolean;
  username: string | null;
  subject: string | null;
  displayName: string | null;
  email: string | null;
  emailVerified: boolean;
  roles: string[];
}
