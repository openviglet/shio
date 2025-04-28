import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioCommitPageComponent } from './shio-commit-page/shio-commit-page.component';
import {AuthGuard} from "../app/_helpers";


const routes: Routes = [
  { path: ':id', component: ShioCommitPageComponent, canActivate: [AuthGuard] }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioPostRoutingModule { }