import { Component, OnInit, Input } from '@angular/core';
import { ShObject } from 'src/object/model/object.model';
import { FormGroup, FormBuilder } from '@angular/forms';
import { faBars } from '@fortawesome/free-solid-svg-icons';
import { NgxSmartModalService } from 'ngx-smart-modal';

@Component({
  selector: 'shio-box-list',
  templateUrl: './shio-box-list.component.html',
  standalone: false
})
export class ShioBoxListComponent implements OnInit {
  @Input() objectList: ShObject | undefined;
  ptSelectForm: FormGroup | undefined;
  faBars = faBars;
  constructor(public ngxSmartModalService: NgxSmartModalService, private formBuilder: FormBuilder) { }

  ngOnInit(): void {
    this.ptSelectForm = this.formBuilder.group({
      filter: ['']
    });
  }

}
