import { Directive, ElementRef, Input, OnChanges, Renderer2, SimpleChanges } from '@angular/core';
import { update } from "jdenticon";
@Directive({ selector: '[identiconHash]' })
export class IdenticonHashDirective implements OnChanges {
  @Input() identiconHash: string | undefined;

  constructor(private el: ElementRef) { }

  ngOnChanges(changes: SimpleChanges) {
    update(this.el.nativeElement, this.identiconHash);
  }
}

