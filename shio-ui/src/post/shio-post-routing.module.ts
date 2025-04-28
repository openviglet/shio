import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioPostPageComponent } from './component/shio-post-page/shio-post-page.component';
import { ShioPostSettingsPageComponent } from './component/shio-post-settings-page/shio-post-settings-page.component';
import {AuthGuard} from "../app/_helpers";


const routes: Routes = [
  { path: ':id', component: ShioPostPageComponent, canActivate: [AuthGuard] },
  { path: ':id/settings', component: ShioPostSettingsPageComponent, canActivate: [AuthGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioPostRoutingModule { }