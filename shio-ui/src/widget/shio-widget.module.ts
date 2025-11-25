import { NgModule } from '@angular/core';
import { ShioTextWidgetComponent } from './component/shio-text-widget/shio-text-widget.component';
import { ShioTextAreaWidgetComponent } from './component/shio-text-area-widget/shio-text-area-widget.component';

@NgModule({
  declarations: [
    ShioTextWidgetComponent,
    ShioTextAreaWidgetComponent
  ],
  imports: [ ],
  exports: [
    ShioTextWidgetComponent,
    ShioTextAreaWidgetComponent
  ]
})
export class ShioWidgetModule { }
