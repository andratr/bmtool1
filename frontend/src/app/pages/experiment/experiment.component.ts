// src/app/experiments/experiment.component.ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule, HttpParams } from '@angular/common/http';

type Row = {
    id: number;
    experimentDate: string;
    numSamples: number;
    numRagSamples: number;
    k: number;
    prompt: string;
    embeddingModel: string;
    llmModel: string;
    metric1Ccc?: number | null;
    metric2TimeMs?: number | null;
    metric3Co2G?: number | null;
};

@Component({
    selector: 'app-experiment',
    standalone: true,
    imports: [CommonModule, FormsModule, HttpClientModule],
    templateUrl: './experiment.component.html',
    styleUrls: ['./experiment.component.css']
})
export class ExperimentComponent implements OnInit {
    private http = inject(HttpClient);
    private baseUrl = '/api/experiments';

    from = this.todayMinusDays(14);
    to = this.todayMinusDays(0);
    embedding = '';
    llm = '';

    loading = false;
    error: string | null = null;
    rows: Row[] = [];

    ngOnInit() { this.reload(); }

    reload() {
        this.loading = true; this.error = null;

        let params = new HttpParams();
        if (this.from) params = params.set('from', this.from);
        if (this.to) params = params.set('to', this.to);
        if (this.embedding.trim()) params = params.set('embedding', this.embedding.trim());
        if (this.llm.trim()) params = params.set('llm', this.llm.trim());

        this.http.get<Row[]>(this.baseUrl, { params }).subscribe({
            next: data => {
                this.rows = [...data].sort((a, b) =>
                    (a.experimentDate < b.experimentDate ? 1 : -1) || (b.id - a.id)
                );
                this.loading = false;
            },
            error: () => { this.error = 'Could not load experiments.'; this.rows = []; this.loading = false; }
        });
    }

    reset() {
        this.from = this.todayMinusDays(14);
        this.to = this.todayMinusDays(0);
        this.embedding = '';
        this.llm = '';
        this.reload();
    }

    show(n?: number | null) { return n == null ? '—' : String(Math.round(n * 100) / 100); }
    private todayMinusDays(n: number) { const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0,10); }
}
