import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioRepositoryPageComponent } from './component/shio-repository-page/shio-repository-page.component';
import { ShioModelingPageComponent } from './component/shio-modeling-page/shio-modeling-page.component';
import { ShioDashboardPageComponent } from './component/shio-dashboard-page/shio-dashboard-page.component';
import {AuthGuard} from "../app/_helpers";

const routes: Routes = [
  {
    path: '', component: ShioDashboardPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'repo', component: ShioRepositoryPageComponent, canActivate: [AuthGuard] },
      { path: 'modeling', component: ShioModelingPageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/content/dashboard/repo', pathMatch: 'full', canActivate: [AuthGuard] }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioDashboardRoutingModule { }