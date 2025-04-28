import { Component, OnInit, Input } from '@angular/core';
import { ShPostTypeReport } from 'src/postType/model/postTypeReport.model';
import { Observable } from 'rxjs';
import { ShSite } from 'src/repository/model/site.model';
import { ShSiteService } from 'src/repository/service/site/site.service';

@Component({
  selector: 'shio-post-type-report',
  templateUrl: './shio-post-type-report.component.html',
  standalone: false
})
export class ShioPostTypeReportComponent implements OnInit {
  @Input() shSite: ShSite | undefined;
  private shSiteService: ShSiteService;
  private postTypeReport: Observable<ShPostTypeReport[]> | undefined;

  constructor(shSiteService: ShSiteService) {
    this.shSiteService = shSiteService;
  }

  getShPostTypeReport(): Observable<ShPostTypeReport[]> | undefined {

    return this.postTypeReport;
  }

  ngOnInit(): void {
    // @ts-ignore
    this.postTypeReport = this.shSiteService.countPostType(this.shSite.id);
  }

}
