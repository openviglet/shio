import { Component, forwardRef } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { createCustomInputControlValueAccessor } from '../input.component';
import { ShioTextWidgetComponent } from '../shio-text-widget/shio-text-widget.component';

const noop = () => {
};

export const CUSTOM_INPUT_CONTROL_VALUE_ACCESSOR: any = {
  provide: NG_VALUE_ACCESSOR,
  useExisting: forwardRef(() => ShioTextAreaWidgetComponent),
  multi: true
};

@Component({
  selector: 'shio-text-area-widget',
  templateUrl: './shio-text-area-widget.component.html',
  standalone: false,
  providers: [createCustomInputControlValueAccessor(ShioTextAreaWidgetComponent)]
})
export class ShioTextAreaWidgetComponent extends ShioTextWidgetComponent { }
