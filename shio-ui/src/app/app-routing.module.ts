import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { ShioLoginPageComponent } from 'src/login/shio-login-page';
import { AuthGuard } from './_helpers';
import { ApiPlaygroundComponent } from './page/api-playground/api-playground.component';
import { ShioContentPageComponent } from './page/shio-content-page/shio-content-page.component';

const routes: Routes = [
  { path: 'login', component: ShioLoginPageComponent },
  {
    path: 'content', component: ShioContentPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'dashboard', loadChildren: () => import('../dashboard/shio-dashboard.module').then(m => m.ShioDashboardModule) },
      { path: 'post', loadChildren: () => import('../post/shio-post.module').then(m => m.ShioPostModule) },
      { path: 'post-type', loadChildren: () => import('../postType/shio-post-type.module').then(m => m.ShioPostTypeModule) },
      { path: 'playground', component: ApiPlaygroundComponent, canActivate: [AuthGuard] },
      { path: 'commit', loadChildren: () => import('../history/shio-history.module').then(m => m.ShioHistoryModule) },
      { path: 'post', loadChildren: () => import('../post/shio-post.module').then(m => m.ShioPostModule) },
      { path: 'object', loadChildren: () => import('../object/shio-object.module').then(m => m.ShioObjectModule) },
      { path: 'repo', loadChildren: () => import('../repository/shio-repository.module').then(m => m.ShioRepositoryModule) },
      { path: '', redirectTo: '/content/dashboard/repo', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/content/dashboard/repo', pathMatch: 'full' }
];
@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
