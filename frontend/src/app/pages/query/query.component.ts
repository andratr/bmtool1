import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

type Citation = {
  mapping: {
    pairId: string;
    plsqlSnippet: string;
    javaSnippet: string;
    plsqlType: string;
    javaType: string;
  };
  score: number;
};

type QueryResponse = {
  text: string;
  citations?: Citation[];
};

type Provider = { id: string; label: string; models: { id: string; label: string }[] };

@Component({
  selector: 'app-query',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './query.component.html',
  styleUrls: ['./query.component.scss'],
})
export class QueryComponent implements OnInit {
  question = '';
  k = 1;
  loading = false;
  result?: QueryResponse;
  error?: string;

  providers: Provider[] = [];
  selectedProvider?: Provider;
  selectedModel?: { id: string; label: string };

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<Provider[]>('/assets/providers.json').subscribe({
      next: (data) => {
        this.providers = data;
        if (this.providers.length) {
          this.selectedProvider = this.providers[0];
          this.selectedModel = this.providers[0].models[0];
        }
      },
      error: (err) => {
        console.error('Failed to load providers.json', err);
        this.error = 'Could not load providers list';
      },
    });
  }

  onProviderChange(provider: Provider) {
    this.selectedProvider = provider;
    this.selectedModel = provider.models[0];
  }

  ask() {
    if (!this.question || !this.selectedProvider || !this.selectedModel) return;

    this.loading = true;
    this.result = undefined;
    this.error = undefined;

    this.http.post<QueryResponse>(`/api/query`, {
      question: this.question,
      k: this.k,
      provider: this.selectedProvider.id,
      model: this.selectedModel.id,
    }).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err: HttpErrorResponse | any) => {
        const status = (err as HttpErrorResponse)?.status;
        const url = (err as HttpErrorResponse)?.url;
        this.error = status
          ? `HTTP ${status} calling ${url ?? '(unknown url)'}: ${err.message || 'Request failed'}`
          : err?.message || 'Request failed';
        this.loading = false;
      },
    });
  }
}
