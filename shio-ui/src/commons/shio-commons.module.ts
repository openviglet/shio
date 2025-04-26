import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShioHeaderComponent } from './component/shio-header/shio-header.component';
import { ShioLogoComponent } from './component/shio-logo/shio-logo.component';
import { IdenticonHashDirective } from '@app/directive/identicon-hash.directive';
import { RouterModule } from '@angular/router';

@NgModule({
  declarations: [
    ShioHeaderComponent,
    ShioLogoComponent,
    IdenticonHashDirective
  ],
  imports: [
    CommonModule,
    RouterModule,
  ],
  exports : [
    ShioHeaderComponent,
    ShioLogoComponent,
    IdenticonHashDirective
  ]

})
export class ShioCommonsModule { }
