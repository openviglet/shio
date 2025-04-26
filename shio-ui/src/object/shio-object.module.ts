import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MomentModule } from 'ngx-moment';
import { ShioRepositoryModule } from 'src/repository/shio-repository.module';
import { ShioBoxListComponent } from './component/shio-box-list/shio-box-list.component';
import { ShioObjectActionsComponent } from './component/shio-object-actions/shio-object-actions.component';
import { ShioObjectListPageComponent } from './component/shio-object-list-page/shio-object-list-page.component';
import { ShioObjectRoutingModule } from './shio-object-routing.module';
import { ShioPostTypeModule } from 'src/postType/shio-post-type.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { NgxSmartModalModule } from 'ngx-smart-modal';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ShioHistoryModule } from 'src/history/shio-history.module';

@NgModule({
  declarations: [
    ShioBoxListComponent,
    ShioObjectActionsComponent,
    ShioObjectListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MomentModule,
    NgxSmartModalModule.forRoot(),
    FontAwesomeModule,
    ShioObjectRoutingModule,
    ShioCommonsModule,
    ShioPostTypeModule,
    ShioRepositoryModule,
    ShioHistoryModule
  ]
})
export class ShioObjectModule { }
