import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { Breadcrumb } from 'src/folder/model/breadcrumb.model';
import { ShPostService } from 'src/post/service/post.service';
import { ShPostXPData } from 'src/post/model/postxp.model';

@Component({
  selector: 'shio-post-page',
  templateUrl: './shio-post-page.component.html',
  standalone: false
})
export class ShioPostPageComponent implements OnInit {
  private breacrumbData: Observable<Breadcrumb>;
  private shPostData: Observable<ShPostXPData>
  private id: string | null;

  constructor(private shPostService: ShPostService, private route: ActivatedRoute, private router: Router) {
    this.id = this.route.snapshot.paramMap.get('id');
    this.shPostData = this.shPostService.get(this.id);

    this.breacrumbData = this.shPostService.getBreadcrumb(this.id);
    this.router.routeReuseStrategy.shouldReuseRoute = function () {
      return false;
    };
  }

  getShPost(): Observable<ShPostXPData> {
    return this.shPostData;
  }
  getBreadcrumb(): Observable<Breadcrumb> {
    return this.breacrumbData;
  }
  ngOnInit(): void {

  }

}
