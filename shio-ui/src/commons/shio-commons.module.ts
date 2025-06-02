import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShioHeaderComponent } from './component/shio-header/shio-header.component';
import { ShioLogoComponent } from './component/shio-logo/shio-logo.component';
import { RouterModule } from '@angular/router';
import {IdenticonHashDirective} from "../app/directive/identicon-hash.directive";

@NgModule({
  declarations: [
    ShioHeaderComponent,
    ShioLogoComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    IdenticonHashDirective,
  ],
  exports : [
    ShioHeaderComponent,
    ShioLogoComponent,
    IdenticonHashDirective
  ]

})
export class ShioCommonsModule { }
