import { Component, OnInit, Input } from '@angular/core';
import { ShSite } from 'src/repository/model/site.model';

@Component({
  selector: 'shio-repository-about',
  templateUrl: './shio-repository-about.component.html',
  standalone: false
})
export class ShioRepositoryAboutComponent implements OnInit {
  @Input() shSite: ShSite | undefined;
  constructor() { }

  ngOnInit(): void {
  }

}
