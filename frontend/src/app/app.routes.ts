import { Routes } from '@angular/router';

import { NotFoundComponent } from './pages/not-found.component';
import { EntryNewComponent } from './pages/entry-new.component';
import { EntryHistoryComponent } from './pages/entry-history.component';
import { PatientDetailComponent } from './pages/patient-detail.component';
import { PatientFormComponent } from './pages/patient-form.component';
import { PatientsListComponent } from './pages/patients-list.component';
import { ReportGenerateComponent } from './pages/report-generate.component';
import { ReportListComponent } from './pages/report-list.component';
import { ReportViewComponent } from './pages/report-view.component';

// Rutas con hash, identicas a las del mockup (F4). `data.title` es el titulo del encabezado y `data.menu`, la
// opcion del menu que queda marcada cuando la pantalla pertenece a otra (ficha de paciente, informe).
// Todas las pantallas de enfermeria del Momento 2 estan hechas (E6, E9, E12b); PendingComponent ya no se usa.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'enf/pacientes' },
  { path: 'enf', pathMatch: 'full', redirectTo: 'enf/pacientes' },
  { path: 'enf/registro', pathMatch: 'full', redirectTo: 'enf/registro/nuevo' },
  { path: 'enf/informes', pathMatch: 'full', redirectTo: 'enf/informes/generar' },

  { path: 'enf/pacientes', component: PatientsListComponent, data: { title: 'Pacientes' } },
  { path: 'enf/pacientes/nuevo', component: PatientFormComponent, data: { title: 'Nuevo paciente', menu: '/enf/pacientes' } },
  { path: 'enf/pacientes/ficha/:id', component: PatientDetailComponent, data: { title: 'Paciente', menu: '/enf/pacientes' } },
  { path: 'enf/registro/nuevo', component: EntryNewComponent, data: { title: 'Nuevo registro diario' } },
  { path: 'enf/registro/nuevo/:id', component: EntryNewComponent, data: { title: 'Nuevo registro diario', menu: '/enf/registro/nuevo' } },
  { path: 'enf/registro/historial', component: EntryHistoryComponent, data: { title: 'Historial de registros' } },
  { path: 'enf/informes/generar', component: ReportGenerateComponent, data: { title: 'Generar informe' } },
  { path: 'enf/informes/generar/:id', component: ReportGenerateComponent, data: { title: 'Generar informe', menu: '/enf/informes/generar' } },
  { path: 'enf/informes/revision', component: ReportListComponent, data: { title: 'Informes en revisión', view: 'revision' } },
  { path: 'enf/informes/aprobados', component: ReportListComponent, data: { title: 'Informes aprobados', view: 'aprobados' } },
  { path: 'enf/informes/ver/:id', component: ReportViewComponent, data: { title: 'Informe de seguimiento', menu: '/enf/informes/revision' } },

  { path: '**', component: NotFoundComponent, data: { title: 'No encontrado' } },
];
