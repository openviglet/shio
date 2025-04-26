import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AppComponent } from './page/app/app.component';
import { BasicAuthInterceptor, ErrorInterceptor } from './_helpers';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { NotifierModule, NotifierOptions } from "angular-notifier-updated";
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { ApiPlaygroundComponent } from './page/api-playground/api-playground.component';
import { ShioContentPageComponent } from './page/shio-content-page/shio-content-page.component';
import { ShioLoginModule } from 'src/login/shio-login.module';
import { ShioPostModule } from 'src/post/shio-post.module';
import { ShioPostTypeModule } from 'src/postType/shio-post-type.module';
import { ShioRepositoryModule } from 'src/repository/shio-repository.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { ShioWidgetModule } from 'src/widget/shio-widget.module';
import { DragDropModule } from '@angular/cdk/drag-drop';

const notifierDefaultOptions: NotifierOptions = {
  position: {
      horizontal: {
          position: "right",
          distance: 12
      },
      vertical: {
          position: "bottom",
          distance: 12,
          gap: 10
      }
  },
  theme: "material",
  behaviour: {
      autoHide: 5000,
      onClick: false,
      onMouseover: "pauseAutoHide",
      showDismissButton: true,
      stacking: 4
  },
  animations: {
      enabled: true,
      show: {
          preset: "slide",
          speed: 300,
          easing: "ease"
      },
      hide: {
          preset: "fade",
          speed: 300,
          easing: "ease",
          offset: 50
      },
      shift: {
          speed: 300,
          easing: "ease"
      },
      overlap: 150
  }
};
@NgModule({
  declarations: [
    AppComponent,
    ApiPlaygroundComponent,
    ShioContentPageComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule,
    NotifierModule.withConfig(notifierDefaultOptions),
    FontAwesomeModule,
    ShioWidgetModule,
    ShioLoginModule,
    ShioPostModule,
    ShioPostTypeModule,
    ShioRepositoryModule,
    ShioCommonsModule,
    DragDropModule

  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: BasicAuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
