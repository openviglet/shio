import { Component, OnInit, Input } from '@angular/core';
import { ShSite } from 'src/repository/model/site.model';
import { ShHistoryService } from 'src/history/service/history.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'shio-repository-tabs',
  templateUrl: './shio-repository-tabs.component.html',
  standalone: false
})
export class ShioRepositoryTabsComponent implements OnInit {
  @Input() shSite: ShSite | undefined;
  @Input() tabIndex: number | undefined;
  private commitCount: Observable<number> | undefined;
  constructor(private shHistoryService: ShHistoryService) {
    
   }

  ngOnInit(): void {
    // @ts-ignore
    this.commitCount = this.shHistoryService.countBySite(this.shSite.id);
  }

  getCommitCount(): Observable<number> | undefined {
    return this.commitCount;
  }

}
