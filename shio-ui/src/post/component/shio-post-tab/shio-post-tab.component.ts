import { Component, OnInit, Input } from '@angular/core';
import { ShPost } from 'src/post/model/post.model';
import { ShPostAttr } from 'src/post/model/postAttr.model';

@Component({
  selector: 'shio-post-tab',
  templateUrl: './shio-post-tab.component.html'
})
export class ShioPostTabComponent implements OnInit {
  @Input() shPost: ShPost;
  @Input() tabIndex: number;
  @Input() currentTab: number;

  constructor() { }

  ngOnInit(): void {
  }
  getTabPostAttrs() {
    let filteredShPostAttrs: ShPostAttr[] = this.shPost.shPostAttrs.filter(
      shPostAttr => shPostAttr.tab === this.tabIndex);
    return filteredShPostAttrs;
  }
}
