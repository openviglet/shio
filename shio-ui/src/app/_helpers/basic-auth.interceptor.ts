import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpXsrfTokenExtractor } from '@angular/common/http';
import { Observable } from 'rxjs';
import {AuthenticationService} from "../_services";
import {environment} from "../../environments/environment";

@Injectable()
export class BasicAuthInterceptor implements HttpInterceptor {
    constructor(private authenticationService: AuthenticationService, private xsrfTokenExtractor: HttpXsrfTokenExtractor) { }

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        const user = this.authenticationService.userValue;
        const isLoggedIn = user && user.authdata;
        const isApiUrl = request.url.startsWith(environment.apiUrl);
        if (isLoggedIn && isApiUrl) {
            request = request.clone({
                setHeaders: {
                    Authorization: `Basic ${user.authdata}`,
                    'X-Requested-With': 'XMLHttpRequest',
                    'Content-Type': 'application/json'
                }
            });

        }

        return next.handle(request);
    }
}