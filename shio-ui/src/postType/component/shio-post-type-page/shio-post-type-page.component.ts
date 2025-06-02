import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ShPostType } from 'src/postType/model/postType.model';
import { ShPostTypeService } from 'src/postType/service/postType.service';

@Component({
  selector: 'app-shio-post-type-page',
  templateUrl: './shio-post-type-page.component.html',
  standalone: false
})
export class ShioPostTypePageComponent implements OnInit {
  private shPostTypeData: Observable<ShPostType>
  private id: string | null;

  constructor(private shPostTypeService: ShPostTypeService, private route: ActivatedRoute) {
    this.id = this.route.snapshot.paramMap.get('id');
    this.shPostTypeData = this.shPostTypeService.get(this.id);

  }

  getShPostType(): Observable<ShPostType> {
    return this.shPostTypeData;
  }

  ngOnInit(): void {

  }


}
