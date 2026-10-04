export interface AuthenticatedUser {
  id: string;
  email: string;
  displayName: string | null;
  /** Set by hand in the database (users.is_admin); gates /admin/*. */
  isAdmin: boolean;
}

export interface AuthenticatedSession {
  id: string;
  deviceId: string;
}

// Global augmentation so every route/middleware file sees req.user/
// req.session without importing anything — populated only by
// middleware/auth.ts's requireAuth, and only present once it has run.
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: AuthenticatedUser;
      session?: AuthenticatedSession;
      /** The profile named by X-ArcTV-Profile, once checked to belong to this account; undefined when the request names none (= the account's own, 'main'). Set by middleware/profile.ts. */
      profileId?: string;
    }
  }
}
