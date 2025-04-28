import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import {User} from "../../../app/_models";
import {AuthenticationService, UserService} from "../../../app/_services";

@Component({
  selector: 'shio-header',
  templateUrl: './shio-header.component.html',
  standalone: false
})
export class ShioHeaderComponent implements OnInit {

  user: Observable<User> | undefined;

  constructor(private userService: UserService, private authenticationService: AuthenticationService) { }


  logout() {
    this.authenticationService.logout();
  }

  ngOnInit(): void {
    this.user = this.userService.getAll();
  }

}
