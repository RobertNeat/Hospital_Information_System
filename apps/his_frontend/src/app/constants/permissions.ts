/**
 * Permission strings ("zasob:akcja") as granted by the backend's `authorities` claim.
 * Copied verbatim from `RolePermissions.java` (source of truth) -- do not invent new
 * strings here; any mismatch silently hides/shows UI incorrectly.
 */
export const PERMISSIONS = {
  VITALS_READ: 'vitals:read',
  VITALS_WRITE: 'vitals:write',

  LAB_ORDER_READ: 'lab-order:read',
  LAB_ORDER_CREATE: 'lab-order:create',
  LAB_ORDER_CANCEL: 'lab-order:cancel',

  IMAGING_ORDER_READ: 'imaging-order:read',
  IMAGING_ORDER_CREATE: 'imaging-order:create',
  IMAGING_ORDER_CANCEL: 'imaging-order:cancel',

  LAB_RESULT_READ: 'lab-result:read',
  IMAGING_RESULT_READ: 'imaging-result:read',

  PRESCRIPTION_READ: 'prescription:read',
  PRESCRIPTION_CREATE: 'prescription:create',
  PRESCRIPTION_CANCEL: 'prescription:cancel',

  MESSAGE_READ: 'message:read',
  MESSAGE_WRITE: 'message:write',
  TASK_READ: 'task:read',
  TASK_WRITE: 'task:write',
  ALERT_READ: 'alert:read',
} as const;
