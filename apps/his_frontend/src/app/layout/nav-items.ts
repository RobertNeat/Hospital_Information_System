export interface NavItem {
  label: string;
  icon: string;
  /** Absolute route, e.g. '/dashboard'. Mutually exclusive with `patientScoped`. */
  route?: string;
  /** Segment appended under `/patients/:id/<segment>` when a patient is in context,
   * otherwise the item links to `/select-patient?next=<segment>`. */
  patientScoped?: string;
  /** Hidden unless the user holds at least one of these permissions. Omitted = always visible
   * (matches a permission granted to every role, e.g. messages). */
  requiresAnyOf?: string[];
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

import { PERMISSIONS } from '../constants/permissions';

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Pulpit',
    items: [{ label: 'Pulpit', icon: 'pi pi-home', route: '/dashboard' }],
  },
  {
    label: 'Pacjenci',
    items: [
      { label: 'Lista pacjentów', icon: 'pi pi-users', route: '/patients' },
      {
        // The wizard always creates a patient first (POST /patients, patient:write);
        // admission:admit alone (e.g. DOCTOR) is not enough to reach this flow.
        label: 'Rejestracja / Przyjęcie',
        icon: 'pi pi-user-plus',
        route: '/patients/register',
        requiresAnyOf: [PERMISSIONS.PATIENT_WRITE],
      },
    ],
  },
  {
    label: 'Dokumentacja medyczna',
    items: [
      {
        label: 'Historia choroby',
        icon: 'pi pi-book',
        patientScoped: 'history',
        requiresAnyOf: [PERMISSIONS.EHR_READ, PERMISSIONS.EHR_READ_LIMITED],
      },
      {
        label: 'Wyniki badań',
        icon: 'pi pi-chart-bar',
        route: '/results',
        requiresAnyOf: [PERMISSIONS.LAB_RESULT_READ, PERMISSIONS.IMAGING_RESULT_READ],
      },
    ],
  },
  {
    label: 'Zlecenia',
    items: [
      {
        label: 'Lista zleceń',
        icon: 'pi pi-list-check',
        route: '/orders',
        requiresAnyOf: [PERMISSIONS.LAB_ORDER_READ, PERMISSIONS.IMAGING_ORDER_READ],
      },
      {
        label: 'Zlecenie laboratoryjne',
        icon: 'pi pi-eye-dropper',
        route: '/orders/lab/new',
        requiresAnyOf: [PERMISSIONS.LAB_ORDER_CREATE],
      },
      {
        label: 'Zlecenie badania obrazowego',
        icon: 'pi pi-image',
        route: '/orders/imaging/new',
        requiresAnyOf: [PERMISSIONS.IMAGING_ORDER_CREATE],
      },
    ],
  },
  {
    label: 'Farmakoterapia',
    items: [
      {
        label: 'Recepty',
        icon: 'pi pi-file-edit',
        route: '/prescriptions',
        requiresAnyOf: [PERMISSIONS.PRESCRIPTION_READ],
      },
      {
        label: 'Nowa recepta',
        icon: 'pi pi-plus-circle',
        route: '/prescriptions/new',
        requiresAnyOf: [PERMISSIONS.PRESCRIPTION_CREATE],
      },
    ],
  },
  {
    label: 'Monitoring',
    items: [
      {
        label: 'Parametry życiowe',
        icon: 'pi pi-heart',
        route: '/vitals',
        requiresAnyOf: [PERMISSIONS.VITALS_READ],
      },
    ],
  },
  {
    label: 'Komunikacja',
    items: [{ label: 'Wiadomości i zadania', icon: 'pi pi-comments', route: '/messages' }],
  },
  {
    label: 'Administracja',
    items: [
      {
        label: 'Pracownicy',
        icon: 'pi pi-users',
        route: '/admin/staff',
        requiresAnyOf: [PERMISSIONS.ACCOUNT_MANAGE],
      },
    ],
  },
];
