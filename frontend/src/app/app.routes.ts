import { Routes } from '@angular/router';
import { QueryComponent } from './pages/query/query.component';
import { ExperimentComponent } from './pages/experiment/experiment.component';
import {HelloComponent} from './pages/hello/hello.component';
import {FactsComponent} from './pages/facts/facts.component';
import {IngestionComponent} from './pages/ingestion/ingestion.component';


export const routes: Routes = [
  { path: '', redirectTo: 'hello', pathMatch: 'full' },
  { path: 'hello', component: HelloComponent },
  { path: 'facts', component: FactsComponent },
  { path: 'query', component: QueryComponent },
  { path: 'experiment', component: ExperimentComponent },
  { path: 'documentation', component: ExperimentComponent },
  { path: 'results', component: ExperimentComponent },
  { path: 'ingestion', component: IngestionComponent }
];
