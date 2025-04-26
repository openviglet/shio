import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ShioPostTypeRoutingModule } from './shio-post-type-routing.module';
import { ShioPostTypeListComponent } from './component/shio-post-type-list/shio-post-type-list.component';
import { ShioPostTypePageComponent } from './component/shio-post-type-page/shio-post-type-page.component';
import { ShioPostTypeReportComponent } from './component/shio-post-type-report/shio-post-type-report.component';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';



@NgModule({
  declarations: [
    ShioPostTypeListComponent,
    ShioPostTypePageComponent,
    ShioPostTypeReportComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ShioCommonsModule,
    ShioPostTypeRoutingModule
  ],
  exports: [
    ShioPostTypeListComponent,
    ShioPostTypeReportComponent
  ]
})
export class ShioPostTypeModule { }
