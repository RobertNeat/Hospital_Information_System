export interface NavItem {
  label: string;
  icon: string;
  /** Absolute route, e.g. '/dashboard'. Mutually exclusive with `patientScoped`. */
  route?: string;
  /** Segment appended under `/patients/:id/<segment>` when a patient is in context,
   * otherwise the item links to `/select-patient?next=<segment>`. */
  patientScoped?: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Pulpit',
    items: [{ label: 'Pulpit', icon: 'pi pi-home', route: '/dashboard' }],
  },
  {
    label: 'Pacjenci',
    items: [
      { label: 'Lista pacjentów', icon: 'pi pi-users', route: '/patients' },
      { label: 'Rejestracja / Przyjęcie', icon: 'pi pi-user-plus', route: '/patients/register' },
    ],
  },
  {
    label: 'Dokumentacja medyczna',
    items: [
      { label: 'Historia choroby', icon: 'pi pi-book', patientScoped: 'history' },
      { label: 'Wyniki badań', icon: 'pi pi-chart-bar', route: '/results' },
    ],
  },
  {
    label: 'Zlecenia',
    items: [
      { label: 'Lista zleceń', icon: 'pi pi-list-check', route: '/orders' },
      { label: 'Zlecenie laboratoryjne', icon: 'pi pi-eye-dropper', route: '/orders/lab/new' },
      { label: 'Zlecenie badania obrazowego', icon: 'pi pi-image', route: '/orders/imaging/new' },
    ],
  },
  {
    label: 'Farmakoterapia',
    items: [
      { label: 'Recepty', icon: 'pi pi-file-edit', route: '/prescriptions' },
      { label: 'Nowa recepta', icon: 'pi pi-plus-circle', route: '/prescriptions/new' },
    ],
  },
  {
    label: 'Monitoring',
    items: [{ label: 'Parametry życiowe', icon: 'pi pi-heart', route: '/vitals' }],
  },
  {
    label: 'Komunikacja',
    items: [{ label: 'Wiadomości i zadania', icon: 'pi pi-comments', route: '/messages' }],
  },
];
