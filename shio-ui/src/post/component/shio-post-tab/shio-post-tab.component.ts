import { Component, OnInit, Input } from '@angular/core';
import { ShPost } from 'src/post/model/post.model';
import { ShPostAttr } from 'src/post/model/postAttr.model';

@Component({
  selector: 'shio-post-tab',
  templateUrl: './shio-post-tab.component.html',
  standalone: false
})
export class ShioPostTabComponent implements OnInit {
  @Input() shPost: ShPost | undefined;
  @Input() tabIndex: number | undefined;
  @Input() currentTab: number | undefined;

  constructor() { }

  ngOnInit(): void {
  }
  getTabPostAttrs() {
    // @ts-ignore
    let filteredShPostAttrs: ShPostAttr[] = this.shPost.shPostAttrs.filter(
      shPostAttr => shPostAttr.tab === this.tabIndex);
    return filteredShPostAttrs;
  }
}
