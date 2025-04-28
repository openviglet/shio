import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioSitePageComponent } from './component/shio-site-page/shio-site-page.component';
import {AuthGuard} from "../app/_helpers";


const routes: Routes = [
  {
    path: ':id',
    component: ShioSitePageComponent,
    canActivate: [AuthGuard]
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioRepositoryRoutingModule { }