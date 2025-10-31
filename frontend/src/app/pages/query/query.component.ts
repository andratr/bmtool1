import {Component, OnInit} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpParams} from '@angular/common/http';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

type Citation = {
    mapping: { pairId: string; plsqlSnippet: string; javaSnippet: string; plsqlType: string; javaType: string; };
    score: number;
};
type FrameworkSymbol = {
    className: string;
    symbol: string;
    kind?: string;
    methodSignature?: string;
    tags?: string[];
    snippet?: string;
};

type FrameworkCitation = {
    symbol?: FrameworkSymbol | null;
    score: number;
};

type QueryResponse = { text: string; citations?: Citation[]; framework?: FrameworkCitation[]; };
type Model = { id: string; label: string; provider?: string; parameters?: string; context?: string | number; };
type Provider = { id: string; label: string; models: Model[] };

@Component({
    selector: 'app-query',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './query.component.html',
    styleUrls: ['./query.component.scss'],
})
export class QueryComponent implements OnInit {
    // form fields
    question = '';
    kDocs = 6;
    kFramework = 6;
    embeddingModel = 'nomic-embed-text:latest';

    // Prompting technique (fallback list ensures dropdown is visible)
    promptingOptions: string[] = [
        'RAG_STANDARD',
        'ZERO_SHOT',
        'FRAMEWORK_FIRST',
        'FEW_SHOT',
        'JSON_STRUCTURED',
        'CRITIQUE_AND_REVISE'
    ];
    selectedPrompting = 'RAG_STANDARD';

    // answer format (default to coding style)
    answerFormat: 'plain' | 'code' = 'code';
    copying = false;

    // ui state
    loading = false;
    result?: QueryResponse;
    error?: string;

    // provider/model state
    providers: Provider[] = [];
    selectedProviderId?: string;
    selectedModelId?: string;

    constructor(private http: HttpClient) {
    }

    ngOnInit() {
        this.loadProviders();
        this.loadPromptingOptions(); // will override the fallback if backend responds
    }

    private loadProviders() {
        this.loading = true;
        this.error = undefined;

        this.http.get<Provider[]>('/api/providers').subscribe({
            next: (data) => {
                this.providers = data ?? [];
                const firstProv = this.providers[0];
                this.selectedProviderId = firstProv?.id;
                this.selectedModelId = firstProv?.models?.[0]?.id;

                this.embeddingModel =
                    this.selectedProviderId === 'ollama' ? 'nomic-embed-text:latest' : 'nomic-embed-text:latest';

                if (!this.selectedProviderId || !this.selectedModelId) {
                    this.error = 'Providers list is empty or has no models.';
                }
                this.loading = false;
            },
            error: (err) => {
                console.error('Failed to load /api/providers', err);
                this.error = 'Could not load providers list';
                this.loading = false;
            },
        });
    }

    private loadPromptingOptions() {
        this.http.get<string[]>('/api/prompting/options').subscribe({
            next: (opts) => {
                if (opts && opts.length) {
                    this.promptingOptions = opts;
                    if (!this.promptingOptions.includes(this.selectedPrompting)) {
                        this.selectedPrompting = this.promptingOptions[0];
                    }
                }
            },
            error: () => {
                // keep the fallback list; no UI break
            },
        });
    }

    get selectedProvider(): Provider | undefined {
        return this.providers.find((p) => p.id === this.selectedProviderId);
    }

    onProviderChange(providerId: string) {
        this.selectedProviderId = providerId;
        const prov = this.providers.find((p) => p.id === providerId);
        this.selectedModelId = prov?.models?.[0]?.id;

        this.embeddingModel =
            providerId === 'ollama' ? 'nomic-embed-text:latest' : this.embeddingModel || 'nomic-embed-text:latest';
    }

    ask() {
        if (!this.question?.trim() || !this.selectedProviderId || !this.selectedModelId) return;

        this.loading = true;
        this.result = undefined;
        this.error = undefined;

        const params = new HttpParams()
            .set('q', this.question)
            .set('kDocs', String(this.kDocs))
            .set('kFramework', String(this.kFramework))
            .set('provider', this.selectedProviderId!)
            .set('llmModel', this.selectedModelId!)
            .set('embeddingModel', this.embeddingModel)
            .set('prompting', this.selectedPrompting || 'RAG_STANDARD');

        this.http.get<QueryResponse>('/api/orchestrator/ask', {params}).subscribe({
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

    copyAnswer() {
        if (!this.result?.text) return;
        this.copying = true;
        navigator.clipboard.writeText(this.result.text).finally(() => {
            setTimeout(() => (this.copying = false), 800);
        });
    }
}
