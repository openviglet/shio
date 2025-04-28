import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import {AuthenticationService} from "../../app/_services";


@Component({ templateUrl: 'shio-login-page.component.html',
    standalone: false })
export class ShioLoginPageComponent implements OnInit {
    loginForm: FormGroup | undefined;
    loading = false;
    submitted = false;
    returnUrl: string | undefined;
    error = '';

    constructor(
        private formBuilder: FormBuilder,
        private route: ActivatedRoute,
        private router: Router,
        private authenticationService: AuthenticationService
    ) { 
        // redirect to home if already logged in
        if (this.authenticationService.userValue) { 
            this.router.navigate(['/']);
        }
    }

    ngOnInit() {
        this.loginForm = this.formBuilder.group({
            username: ['', Validators.required],
            password: ['', Validators.required]
        });

        // get return url from route parameters or default to '/'
        this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';
    }

    // convenience getter for easy access to form fields
    get f() { // @ts-ignore
        return this.loginForm.controls; }

    onSubmit() {
        this.submitted = true;

        // stop here if form is invalid
        // @ts-ignore
        if (this.loginForm.invalid) {
            return;
        }

        this.loading = true;
        this.authenticationService.login(this.f.username.value, this.f.password.value)
            .pipe(first())
            .subscribe(
                data => {
                    this.router.navigate([this.returnUrl]);
                },
                (error: string) => {
                    this.error = error;
                    this.loading = false;
                });
    }
}
