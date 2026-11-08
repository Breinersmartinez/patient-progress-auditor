import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { roleGuard } from './core/role.guard';
import { roleHome } from './core/roles';
import { SessionService } from './core/session.service';
import { NotFoundComponent } from './pages/not-found.component';
import { EntryNewComponent } from './pages/entry-new.component';
import { EntryHistoryComponent } from './pages/entry-history.component';
import { PatientDetailComponent } from './pages/patient-detail.component';
import { PatientFormComponent } from './pages/patient-form.component';
import { PatientsListComponent } from './pages/patients-list.component';
import { ReportGenerateComponent } from './pages/report-generate.component';
import { ReportListComponent } from './pages/report-list.component';
import { ReportViewComponent } from './pages/report-view.component';

// Rutas con hash, identicas a las del mockup (F4), con un prefijo por rol (D26: enf o med; el rol sale del usuario
// de demostracion elegido, no de la URL). `data.title` es el titulo del encabezado y `data.menu`, la opcion del
// menu que queda marcada cuando la pantalla pertenece a otra (ficha de paciente, informe). `canMatch` con
// `roleGuard` es la guarda de pantalla (no la barrera: eso lo decide el servidor) y lleva al inicio del rol
// propio si el usuario elegido no es del que corresponde a la ruta. PendingComponent ya no se usa.
export const routes: Routes = [
  // Vuelve al inicio del rol de la sesion actual, no siempre a enfermeria.
  { path: '', pathMatch: 'full', redirectTo: () => roleHome(inject(SessionService).session()?.role) },

  { path: 'enf', pathMatch: 'full', redirectTo: 'enf/pacientes' },
  { path: 'enf/registro', pathMatch: 'full', redirectTo: 'enf/registro/nuevo' },
  { path: 'enf/informes', pathMatch: 'full', redirectTo: 'enf/informes/generar' },

  { path: 'enf/pacientes', component: PatientsListComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Pacientes' } },
  { path: 'enf/pacientes/nuevo', component: PatientFormComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Nuevo paciente', menu: '/enf/pacientes' } },
  { path: 'enf/pacientes/ficha/:id', component: PatientDetailComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Paciente', menu: '/enf/pacientes' } },
  { path: 'enf/registro/nuevo', component: EntryNewComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Nuevo registro diario' } },
  { path: 'enf/registro/nuevo/:id', component: EntryNewComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Nuevo registro diario', menu: '/enf/registro/nuevo' } },
  { path: 'enf/registro/historial', component: EntryHistoryComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Historial de registros' } },
  { path: 'enf/informes/generar', component: ReportGenerateComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Generar informe' } },
  { path: 'enf/informes/generar/:id', component: ReportGenerateComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Generar informe', menu: '/enf/informes/generar' } },
  { path: 'enf/informes/revision', component: ReportListComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Informes en revisión', view: 'revision' } },
  { path: 'enf/informes/aprobados', component: ReportListComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Informes aprobados', view: 'aprobados' } },
  { path: 'enf/informes/ver/:id', component: ReportViewComponent, canMatch: [roleGuard('ENFERMERIA')], data: { title: 'Informe de seguimiento', menu: '/enf/informes/revision' } },

  { path: 'med', pathMatch: 'full', redirectTo: 'med/pacientes' },

  { path: 'med/pacientes', component: PatientsListComponent, canMatch: [roleGuard('MEDICO')], data: { title: 'Mis pacientes' } },
  { path: 'med/pacientes/ficha/:id', component: PatientDetailComponent, canMatch: [roleGuard('MEDICO')], data: { title: 'Paciente', menu: '/med/pacientes' } },
  { path: 'med/registro/nuevo/:id', component: EntryNewComponent, canMatch: [roleGuard('MEDICO')], data: { title: 'Nuevo registro diario', menu: '/med/pacientes' } },
  { path: 'med/informes/recibidos', component: ReportListComponent, canMatch: [roleGuard('MEDICO')], data: { title: 'Informes recibidos', view: 'recibidos' } },
  { path: 'med/informes/ver/:id', component: ReportViewComponent, canMatch: [roleGuard('MEDICO')], data: { title: 'Informe de seguimiento', menu: '/med/informes/recibidos' } },

  { path: '**', component: NotFoundComponent, data: { title: 'No encontrado' } },
];
