import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'shio-object-actions',
  templateUrl: './shio-object-actions.component.html',
  standalone: false
})
export class ShioObjectActionsComponent implements OnInit {
  @Input() title: string | undefined;
  constructor() { }

  ngOnInit(): void {
  }

}
