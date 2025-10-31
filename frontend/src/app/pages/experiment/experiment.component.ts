import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule, HttpParams } from '@angular/common/http';

type Row = {
    id: number;
    experimentDate: string;

    // new schema
    fwHitsCount: number;
    docHitsCount: number;
    kFw: number;
    kDoc: number;

    prompt: string;
    embeddingModel: string;
    llmModel: string;

    metric1Ccc?: number | null;
    metric2TimeMs?: number | null;
    metric3Co2G?: number | null;
    metric4PromptTok?: number | null;
    metric5CompletionTok?: number | null;
    metric6TotalTok?: number | null;
    promptingTechnique?: string | null;

    // legacy (optional; mapped into new columns during load)
    numSamples?: number;
    numRagSamples?: number;
    k?: number;
};

@Component({
    selector: 'app-experiment',
    standalone: true,
    imports: [CommonModule, FormsModule, HttpClientModule],
    templateUrl: './experiment.component.html',
    styleUrls: ['./experiment.component.css'],
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
        this.loading = true;
        this.error = null;

        let params = new HttpParams();
        if (this.from) params = params.set('from', this.from);
        if (this.to) params = params.set('to', this.to);
        if (this.embedding.trim()) params = params.set('embedding', this.embedding.trim());
        if (this.llm.trim()) params = params.set('llm', this.llm.trim());

        this.http.get<any[]>(this.baseUrl, { params }).subscribe({
            next: (data) => {
                // Map backend payload defensively to our Row type.
                this.rows = (data || []).map((e: any): Row => {
                    // derive tokens total if not provided
                    const pt = coerceInt(e.metric4PromptTok);
                    const ct = coerceInt(e.metric5CompletionTok);
                    const tt = e.metric6TotalTok != null ? coerceInt(e.metric6TotalTok) :
                        (pt != null || ct != null) ? ((pt ?? 0) + (ct ?? 0)) : null;

                    // legacy support: if kFw/kDoc missing, fall back to single k or 0
                    const kFw = e.kFw != null ? e.kFw : (e.k ?? 0);
                    const kDoc = e.kDoc != null ? e.kDoc : (e.k ?? 0);

                    // legacy support: if fw/doc hit counts missing, derive from samples if present
                    const fwHitsCount = e.fwHitsCount != null ? e.fwHitsCount :
                        (typeof e.numSamples === 'number' && typeof e.numRagSamples === 'number'
                            ? (e.numSamples - e.numRagSamples) : 0);
                    const docHitsCount = e.docHitsCount != null ? e.docHitsCount :
                        (typeof e.numRagSamples === 'number' ? e.numRagSamples : 0);

                    return {
                        id: e.id,
                        experimentDate: e.experimentDate,
                        fwHitsCount,
                        docHitsCount,
                        kFw,
                        kDoc,
                        prompt: e.prompt,
                        embeddingModel: e.embeddingModel,
                        llmModel: e.llmModel,
                        metric1Ccc: coerceNum(e.metric1Ccc),
                        metric2TimeMs: coerceNum(e.metric2TimeMs),
                        metric3Co2G: coerceNum(e.metric3Co2G),
                        metric4PromptTok: pt,
                        metric5CompletionTok: ct,
                        metric6TotalTok: tt,
                        promptingTechnique: e.promptingTechnique ?? null,

                        // keep legacy on the object (not used by template)
                        numSamples: e.numSamples,
                        numRagSamples: e.numRagSamples,
                        k: e.k,
                    };
                }).sort((a, b) =>
                    (a.experimentDate < b.experimentDate ? 1 : -1) || (b.id - a.id)
                );

                this.loading = false;
            },
            error: () => {
                this.error = 'Could not load experiments.';
                this.rows = [];
                this.loading = false;
            }
        });
    }

    reset() {
        this.from = this.todayMinusDays(14);
        this.to = this.todayMinusDays(0);
        this.embedding = '';
        this.llm = '';
        this.reload();
    }

    show(n?: number | null) {
        if (n == null || Number.isNaN(n)) return '—';
        // show integers as-is; decimals to 2 dp
        const isInt = Math.abs(n - Math.trunc(n)) < 1e-9;
        return isInt ? String(Math.trunc(n)) : String(Math.round(n * 100) / 100);
    }

    private todayMinusDays(n: number) {
        const d = new Date();
        d.setDate(d.getDate() - n);
        return d.toISOString().slice(0, 10);
    }
}

function coerceNum(v: any): number | null {
    if (v == null) return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}
function coerceInt(v: any): number | null {
    if (v == null) return null;
    const n = Number(v);
    return Number.isFinite(n) ? Math.round(n) : null;
}
