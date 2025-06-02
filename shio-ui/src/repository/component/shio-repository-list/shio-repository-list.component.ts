import { Component, OnInit, Input } from '@angular/core';
import { ShSite } from 'src/repository/model/site.model';

@Component({
  selector: 'shio-repository-list',
  templateUrl: './shio-repository-list.component.html',
  standalone: false
})
export class ShioRepositoryListComponent implements OnInit {
  @Input() shSites: ShSite[] | undefined;
  constructor() { }

  ngOnInit(): void {
  }

}
