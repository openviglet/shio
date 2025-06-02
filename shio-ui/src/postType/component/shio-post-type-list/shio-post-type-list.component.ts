import { Component, OnInit, Input } from '@angular/core';
import { ShPostType } from 'src/postType/model/postType.model';

@Component({
  selector: 'shio-post-type-list',
  templateUrl: './shio-post-type-list.component.html',
  standalone: false
})
export class ShioPostTypeListComponent implements OnInit {
  @Input() shPostTypes: ShPostType[] | undefined;
  constructor() { }

  ngOnInit(): void {
  }

}
