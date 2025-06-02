import { Component, forwardRef, ViewChild, ElementRef, OnInit } from '@angular/core';
import { NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';
export function createCustomInputControlValueAccessor(extendedInputComponent: any) {
    return {
        provide: NG_VALUE_ACCESSOR,
        useExisting: forwardRef(() => extendedInputComponent),
        multi: true
    };
}

@Component({
    template: ''
})
export class InputComponent implements ControlValueAccessor, OnInit {
    @ViewChild('input') inputRef: ElementRef | undefined;

    // The internal data model
    public innerValue: any = '';

    // Placeholders for the callbacks which are later provided
    // by the Control Value Accessor
    private onChangeCallback: any;

    // implements ControlValueAccessor interface
    writeValue(value: any) {
        if (value !== this.innerValue) {
            this.innerValue = value;
        }
    }
    // implements ControlValueAccessor interface
    registerOnChange(fn: any) {
        this.onChangeCallback = fn;
    }

    // implements ControlValueAccessor interface - not used, used for touch input
    registerOnTouched() { }

    // change events from the textarea
    private onChange() {
        // @ts-ignore
        const input = <HTMLInputElement>this.inputRef.nativeElement;
        // get value from text area
        const newValue = input.value;

        // update the form
        this.onChangeCallback(newValue);
    }
    ngOnInit() {
        // @ts-ignore
        const inputElement = <HTMLInputElement>this.inputRef.nativeElement;
        inputElement.onchange = () => this.onChange();
        inputElement.onkeyup = () => this.onChange();
    }
}