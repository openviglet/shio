import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ShObjectService } from 'src/object/service/object.service';
import { ShObject } from 'src/object/model/object.model';

@Component({
  selector: 'shio-object-list-page',
  templateUrl: './shio-object-list-page.component.html',
  standalone: false
})
export class ShioObjectListPageComponent implements OnInit, OnDestroy {
  private shObjectList: Observable<ShObject>;
  constructor(private shObject: ShObjectService, private route: ActivatedRoute, private router: Router) {
    let id = this.route.snapshot.paramMap.get('id');
    this.shObjectList = this.shObject.get(id);
    this.router.routeReuseStrategy.shouldReuseRoute = function () {
      return false;
    };
  }
  getShObjectList(): Observable<ShObject> {

    return this.shObjectList;
  }
  ngOnInit() {   
  }
  ngOnDestroy(): void {

  }
}
