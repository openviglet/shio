import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ShSite } from 'src/repository/model/site.model';
import { Observable } from 'rxjs';
import { ShHistoryService } from 'src/history/service/history.service';
import { ShHistory } from 'src/history/model/history.model';
import { ShSiteService } from 'src/repository/service/site/site.service';

@Component({
  selector: 'app-shio-commit-page',
  templateUrl: './shio-commit-page.component.html',
  standalone: false
})
export class ShioCommitPageComponent implements OnInit {
  private shSite: Observable<ShSite>;
  private shHistories: Observable<ShHistory[]>;
  today: Date = new Date();
  historyGroupBy: any;
  constructor(private shSiteService: ShSiteService, private shHistoryService: ShHistoryService, private route: ActivatedRoute,) {
    let id = this.route.snapshot.paramMap.get('id');
    this.shSite = this.shSiteService.get(id);
    this.shHistories = this.shHistoryService.findBySite(id, 0);
    this.shHistories.subscribe(histories => {
      histories.map(history => {
        let date = new Date(history.date);
        date.setHours(0, 0, 0, 0);
        history.day = new Date(date.toString());
      })
      this.historyGroupBy = this.groupBy(histories, 'day');
      console.log(this.historyGroupBy);
    })

  }

  ngOnInit(): void {
  }
  getShSite(): Observable<ShSite> {

    return this.shSite;
  }
  getShHistories(): Observable<ShHistory[]> {

    return this.shHistories;
  }

  groupBy(xs: any, key: string) {
    let reduce = xs.reduce(function (rv: any, x: any) {
      (rv[x[key]] = rv[x[key]] || []).push(x);
      return rv;
    }, {});
    let groupByDay: { day: Date; histories: unknown; }[] = [];
    Object.entries(reduce).forEach((itemByDay) => {
      groupByDay.push({
        day: new Date(itemByDay[0]),
        histories: itemByDay[1]
      });
    });
    return groupByDay;
  }
}
