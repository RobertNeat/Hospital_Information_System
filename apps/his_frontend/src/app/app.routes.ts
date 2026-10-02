import type { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell/app-shell';
import { patientResolver } from './resolvers/patient.resolver';
import { authGuard, guestGuard, permissionGuard } from './guards/auth.guard';
import { unsavedChangesGuard } from './guards/unsaved-changes.guard';
import { patientScopedRedirect } from './utils/patient-scoped-redirect';
import { PERMISSIONS } from './constants/permissions';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login-page').then((m) => m.LoginPage),
    title: 'Logowanie',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register-page').then((m) => m.RegisterPage),
    title: 'Rejestracja konta',
  },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./pages/dashboard/dashboard-page').then((m) => m.DashboardPage),
        title: 'Pulpit',
      },
      {
        path: 'patients',
        loadComponent: () =>
          import('./pages/patient-list/patient-list-page').then((m) => m.PatientListPage),
        title: 'Pacjenci',
      },
      {
        path: 'patients/register',
        loadComponent: () =>
          import('./pages/patient-registration/patient-registration-page').then(
            (m) => m.PatientRegistrationPage,
          ),
        data: { mode: 'create' },
        canDeactivate: [unsavedChangesGuard],
        title: 'Rejestracja pacjenta',
      },
      {
        path: 'patients/:patientId',
        loadComponent: () =>
          import('./pages/patient-chart/patient-chart-page').then((m) => m.PatientChartPage),
        resolve: { patient: patientResolver },
        runGuardsAndResolvers: 'paramsChange',
        title: 'Karta pacjenta',
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'overview' },
          {
            path: 'overview',
            loadComponent: () =>
              import('./pages/patient-overview/patient-overview-page').then(
                (m) => m.PatientOverviewPage,
              ),
            title: 'Dane pacjenta',
          },
          {
            path: 'edit',
            loadComponent: () =>
              import('./pages/patient-registration/patient-registration-page').then(
                (m) => m.PatientRegistrationPage,
              ),
            data: { mode: 'edit' },
            canDeactivate: [unsavedChangesGuard],
            title: 'Edycja danych pacjenta',
          },
          {
            path: 'history',
            loadComponent: () =>
              import('./pages/patient-history/patient-history-page').then(
                (m) => m.PatientHistoryPage,
              ),
            title: 'Historia choroby',
          },
          {
            path: 'results',
            loadComponent: () =>
              import('./pages/patient-results/patient-results-page').then(
                (m) => m.PatientResultsPage,
              ),
            title: 'Wyniki badań',
          },
          {
            path: 'results/lab/:resultId',
            loadComponent: () =>
              import('./pages/result-detail/result-detail-page').then((m) => m.ResultDetailPage),
            data: { kind: 'lab' },
            title: 'Wynik badania',
          },
          {
            path: 'results/imaging/:resultId',
            loadComponent: () =>
              import('./pages/result-detail/result-detail-page').then((m) => m.ResultDetailPage),
            data: { kind: 'imaging' },
            title: 'Opis badania',
          },
          {
            path: 'vitals',
            canActivate: [permissionGuard(PERMISSIONS.VITALS_READ)],
            loadComponent: () =>
              import('./pages/patient-vitals/patient-vitals-page').then((m) => m.PatientVitalsPage),
            title: 'Parametry życiowe',
          },
          {
            path: 'orders',
            canActivate: [
              permissionGuard(PERMISSIONS.LAB_ORDER_READ, PERMISSIONS.IMAGING_ORDER_READ),
            ],
            loadComponent: () =>
              import('./pages/patient-orders/patient-orders-page').then((m) => m.PatientOrdersPage),
            title: 'Zlecenia pacjenta',
          },
          {
            path: 'orders/lab/new',
            canActivate: [permissionGuard(PERMISSIONS.LAB_ORDER_CREATE)],
            loadComponent: () =>
              import('./pages/lab-order-wizard/lab-order-wizard-page').then(
                (m) => m.LabOrderWizardPage,
              ),
            canDeactivate: [unsavedChangesGuard],
            title: 'Nowe zlecenie laboratoryjne',
          },
          {
            path: 'orders/imaging/new',
            canActivate: [permissionGuard(PERMISSIONS.IMAGING_ORDER_CREATE)],
            loadComponent: () =>
              import('./pages/imaging-order-wizard/imaging-order-wizard-page').then(
                (m) => m.ImagingOrderWizardPage,
              ),
            canDeactivate: [unsavedChangesGuard],
            title: 'Nowe zlecenie badania obrazowego',
          },
          {
            path: 'prescriptions',
            canActivate: [permissionGuard(PERMISSIONS.PRESCRIPTION_READ)],
            loadComponent: () =>
              import('./pages/patient-prescriptions/patient-prescriptions-page').then(
                (m) => m.PatientPrescriptionsPage,
              ),
            title: 'Leki i recepty',
          },
          {
            path: 'prescriptions/new',
            canActivate: [permissionGuard(PERMISSIONS.PRESCRIPTION_CREATE)],
            loadComponent: () =>
              import('./pages/prescription-wizard/prescription-wizard-page').then(
                (m) => m.PrescriptionWizardPage,
              ),
            canDeactivate: [unsavedChangesGuard],
            title: 'Nowa recepta',
          },
        ],
      },
      {
        path: 'select-patient',
        loadComponent: () =>
          import('./pages/patient-picker/patient-picker-page').then((m) => m.PatientPickerPage),
        title: 'Wybór pacjenta',
      },
      {
        path: 'results',
        canActivate: [
          permissionGuard(PERMISSIONS.LAB_RESULT_READ, PERMISSIONS.IMAGING_RESULT_READ),
        ],
        loadComponent: () =>
          import('./pages/results-inbox/results-inbox-page').then((m) => m.ResultsInboxPage),
        title: 'Wyniki badań',
      },
      {
        path: 'orders',
        canActivate: [permissionGuard(PERMISSIONS.LAB_ORDER_READ, PERMISSIONS.IMAGING_ORDER_READ)],
        loadComponent: () =>
          import('./pages/orders-worklist/orders-worklist-page').then((m) => m.OrdersWorklistPage),
        title: 'Zlecenia',
      },
      {
        path: 'orders/lab/new',
        pathMatch: 'full',
        redirectTo: patientScopedRedirect('orders/lab/new'),
      },
      {
        path: 'orders/imaging/new',
        pathMatch: 'full',
        redirectTo: patientScopedRedirect('orders/imaging/new'),
      },
      {
        path: 'prescriptions',
        canActivate: [permissionGuard(PERMISSIONS.PRESCRIPTION_READ)],
        loadComponent: () =>
          import('./pages/prescriptions-list/prescriptions-list-page').then(
            (m) => m.PrescriptionsListPage,
          ),
        title: 'Recepty',
      },
      {
        path: 'prescriptions/new',
        pathMatch: 'full',
        redirectTo: patientScopedRedirect('prescriptions/new'),
      },
      {
        path: 'vitals',
        canActivate: [permissionGuard(PERMISSIONS.VITALS_READ)],
        loadComponent: () =>
          import('./pages/vitals-board/vitals-board-page').then((m) => m.VitalsBoardPage),
        title: 'Monitoring parametrów',
      },
      {
        path: 'vitals/:patientId',
        pathMatch: 'full',
        redirectTo: ({ params }) => `/patients/${params['patientId']}/vitals`,
      },
      {
        path: 'messages',
        loadComponent: () => import('./pages/messages/messages-page').then((m) => m.MessagesPage),
        title: 'Komunikacja zespołowa',
      },
      {
        path: '**',
        loadComponent: () => import('./pages/not-found/not-found-page').then((m) => m.NotFoundPage),
        title: 'Nie znaleziono',
      },
    ],
  },
];
