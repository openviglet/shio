import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShioObjectListPageComponent } from './component/shio-object-list-page/shio-object-list-page.component';
import {AuthGuard} from "../app/_helpers";


const routes: Routes = [
  {
    path: 'list/:id',
    component: ShioObjectListPageComponent, 
    canActivate: [AuthGuard]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ShioObjectRoutingModule { }