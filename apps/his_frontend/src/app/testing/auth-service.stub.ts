import { AuthService } from '../services/auth.service';

/**
 * Test provider granting every permission, so existing specs that predate permission
 * gating keep exercising their happy path (equivalent to a doctor/admin session) unless
 * a spec explicitly restricts `hasPermission` to test the gating itself.
 */
export const authServiceStub = {
  provide: AuthService,
  useValue: { hasPermission: () => true },
};
