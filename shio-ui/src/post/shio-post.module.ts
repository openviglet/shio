import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShioPostPageComponent } from './component/shio-post-page/shio-post-page.component';
import { ShioPostSettingsPageComponent } from './component/shio-post-settings-page/shio-post-settings-page.component';
import { ShioPostTabComponent } from './component/shio-post-tab/shio-post-tab.component';
import { ShioPostTabsComponent } from './component/shio-post-tabs/shio-post-tabs.component';
import { ShPostService } from './service/post.service';
import { ShioPostRoutingModule } from './shio-post-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ShioWidgetModule } from 'src/widget/shio-widget.module';

@NgModule({
  declarations: [
    ShioPostPageComponent,
    ShioPostSettingsPageComponent,
    ShioPostTabComponent,
    ShioPostTabsComponent

  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ShioCommonsModule,
    ShioPostRoutingModule,
    ShioWidgetModule
  ],
  providers: [
    ShPostService
  ],
})
export class ShioPostModule { }
