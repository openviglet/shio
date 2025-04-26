import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShioDashboardRoutingModule } from './shio-dashboard-routing.module';
import { ShioPostTypeModule } from 'src/postType/shio-post-type.module';
import { ShioRepositoryModule } from 'src/repository/shio-repository.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ShioDashboardPageComponent } from './component/shio-dashboard-page/shio-dashboard-page.component';
import { ShioModelingPageComponent } from './component/shio-modeling-page/shio-modeling-page.component';
import { ShioRepositoryPageComponent } from './component/shio-repository-page/shio-repository-page.component';

@NgModule({
  declarations: [
    ShioDashboardPageComponent,
    ShioModelingPageComponent,
    ShioRepositoryPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ShioCommonsModule,
    ShioDashboardRoutingModule,
    ShioRepositoryModule,
    ShioPostTypeModule
  ]
})
export class ShioDashboardModule { }
