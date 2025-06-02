import { Component, OnInit } from '@angular/core';
import { ShSite } from 'src/repository/model/site.model';
import { Observable } from 'rxjs';
import { ShSiteService } from 'src/repository/service/site/site.service';
import {User} from "../../../app/_models";
import {UserService} from "../../../app/_services";

@Component({
  selector: 'app-shio-repository-page',
  templateUrl: './shio-repository-page.component.html',
  standalone: false
})
export class ShioRepositoryPageComponent implements OnInit {

  sites: ShSite[] | undefined;
  orderProp: string;
  loading = false;
  user: Observable<User> | undefined;
  constructor(private userService: UserService, siteService: ShSiteService) {
    siteService.query().subscribe(sites => {
      this.sites = sites;
    });
    this.orderProp = 'name';
  }
  getSites(): ShSite[] | undefined {
    return this.sites;
  }

  ngOnInit() {
    this.loading = true;
    this.user = this.userService.getAll();
  }
}
