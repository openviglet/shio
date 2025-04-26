import { Component, OnInit, Input } from '@angular/core';
import { Breadcrumb } from 'src/folder/model/breadcrumb.model';
import { Observable } from 'rxjs';
import { ShPostService } from 'src/post/service/post.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ShPost } from 'src/post/model/post.model';
import { NotifierService } from 'angular-notifier-updated';

@Component({
  selector: 'shio-post-tabs',
  templateUrl: './shio-post-tabs.component.html'
})
export class ShioPostTabsComponent implements OnInit {
  @Input() shPost: ShPost;
  public currentTab: number = 0;
  private breacrumbData: Observable<Breadcrumb>;
  private id: string;
  tabs: any[] = [];
  constructor(private readonly notifier: NotifierService, private shPostService: ShPostService, private route: ActivatedRoute, private router: Router) {
    this.id = this.route.snapshot.paramMap.get('id');
    this.breacrumbData = this.shPostService.getBreadcrumb(this.id);
    this.router.routeReuseStrategy.shouldReuseRoute = function () {
      return false;
    };
  }
  getBreadcrumb(): Observable<Breadcrumb> {
    return this.breacrumbData;
  }
  ngOnInit(): void {
    this.formatPost(this.shPost);
  }
  private formatPost(shPost: ShPost) {
    this.tabs = [];
    let currentTabIndex: number;
    let createTab = false;
    shPost.shPostAttrs.sort((a, b) => a.shPostTypeAttr.ordinal - b.shPostTypeAttr.ordinal);

    shPost.shPostAttrs.forEach((shPostAttr, index) => {
      let tabName = this.shPost.shPostType.title;

      if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab') {
        tabName = shPostAttr.shPostTypeAttr.label;
        currentTabIndex = index;
        createTab = true;
      } else if (index == 0) {
        currentTabIndex = index;
        createTab = true;
      }

      if (createTab) {
        this.tabs.push({
          ordinal: index,
          name: tabName
        });
        createTab = false;
      }

      shPostAttr.tab = currentTabIndex;
    });
  }
  public savePost() {
    this.shPostService.savePost(this.shPost).subscribe(
      (shPost: ShPost) => {
        this.shPost = shPost;
        this.formatPost(this.shPost);
        this.notifier.notify("success", shPost.title.concat(" Post was updated."));
      },
      response => {
        this.notifier.notify("error", "Repository settings was error: " + response);
      },
      () => {
        // The POST observable is now completed
      });
  }
}
