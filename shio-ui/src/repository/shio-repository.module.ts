import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShioRepositoryAboutComponent } from './component/shio-repository-about/shio-repository-about.component';
import { ShioRepositoryListComponent } from './component/shio-repository-list/shio-repository-list.component';
import { ShioRepositoryTabsComponent } from './component/shio-repository-tabs/shio-repository-tabs.component';
import { ShSiteService } from './service/site/site.service';
import { ShioSitePageComponent } from './component/shio-site-page/shio-site-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ShioRepositoryRoutingModule } from './shio-repository-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { ShHistoryService } from 'src/history/service/history.service';

@NgModule({
  declarations: [
    ShioRepositoryAboutComponent,
    ShioRepositoryListComponent,
    ShioRepositoryTabsComponent,
    ShioSitePageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ShioRepositoryRoutingModule,
    ShioCommonsModule
  ],
  exports: [
    ShioRepositoryAboutComponent,
    ShioRepositoryListComponent,
    ShioRepositoryTabsComponent
  ],
  providers: [
    ShSiteService,
    ShHistoryService
  ]
})
export class ShioRepositoryModule { }
