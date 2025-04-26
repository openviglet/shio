import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { ShSite } from 'src/repository/model/site.model';
import { NotifierService } from 'angular-notifier-updated';
import { ShSiteService } from 'src/repository/service/site/site.service';

@Component({
  selector: 'app-shio-site-page',
  templateUrl: './shio-site-page.component.html'
})
export class ShioSitePageComponent implements OnInit {
  private shSite: Observable<ShSite>;

  constructor(private readonly notifier: NotifierService, private shSiteService: ShSiteService, private route: ActivatedRoute) {
    let id = this.route.snapshot.paramMap.get('id');
    this.shSite = this.shSiteService.get(id);
  }

  getShSite(): Observable<ShSite> {

    return this.shSite;
  }

  ngOnInit(): void {
  }

  public saveSite(_shSite: ShSite) {
    this.shSiteService.save(_shSite).subscribe(
      (shSite: ShSite) => {
        _shSite = shSite;
        this.notifier.notify("success", shSite.name.concat(" Repository settings was updated."));
      },
      response => {
        this.notifier.notify("error", "Repository settings was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
