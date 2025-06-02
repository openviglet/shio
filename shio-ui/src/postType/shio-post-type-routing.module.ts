import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioPostTypePageComponent } from './component/shio-post-type-page/shio-post-type-page.component';
import {AuthGuard} from "../app/_helpers";

const routes: Routes = [
  { path: ':id', component: ShioPostTypePageComponent, canActivate: [AuthGuard] }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioPostTypeRoutingModule { }