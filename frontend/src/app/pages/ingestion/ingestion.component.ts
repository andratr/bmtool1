import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
    FormArray, FormBuilder, FormControl, FormGroup,
    ReactiveFormsModule, Validators
} from '@angular/forms';
import { HttpClient, HttpClientModule, HttpEvent, HttpEventType } from '@angular/common/http';
import { interval, Subscription } from 'rxjs';

type JobState = 'RUNNING' | 'DONE' | 'FAILED';

interface JobStatus {
    id: string;
    type: string;
    state: JobState;
    message: string;
    processed: number;
    total: number;
}

@Component({
    selector: 'app-ingestion',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, HttpClientModule],
    templateUrl: './ingestion.component.html',
    styleUrls: ['./ingestion.component.css']
})
export class IngestionComponent implements OnDestroy {
    // legacy (kept):
    ragForm!: FormGroup<{ rootDir: FormControl<string> }>;
    frameworkForm!: FormGroup<{ pkgs: FormArray<FormControl<string>> }>;

    // selections
    ragFiles: File[] = [];
    frameworkFiles: File[] = [];
    ragFolderName = '';
    frameworkFolderName = '';
    ragFileCount = 0;
    frameworkFileCount = 0;

    // ui state
    busy = false;
    overallProgress = 0;
    lastError: string | null = null;

    // jobs
    private pollers = new Map<string, Subscription>();
    jobFeed: JobStatus[] = [];

    constructor(private fb: FormBuilder, private http: HttpClient) {
        this.ragForm = this.fb.nonNullable.group({
            rootDir: this.fb.nonNullable.control('', Validators.required)
        });
        this.frameworkForm = this.fb.nonNullable.group({
            pkgs: this.fb.nonNullable.array([this.fb.nonNullable.control('', Validators.required)])
        });
    }

    // ----- helpers -----
    get isUploadInvalid(): boolean {
        return this.ragFileCount === 0 && this.frameworkFileCount === 0;
    }

    private folderNameFrom(files: File[]): string {
        if (!files.length) return '';
        const any = files[0] as File & { webkitRelativePath?: string };
        const rel = any.webkitRelativePath || '';
        return rel.split('/')[0] || '(root)';
    }

    private mergeFiles(target: File[], incoming: File[]): File[] {
        const key = (f: File) => `${f.name}::${f.size}::${f.lastModified}`;
        const map = new Map<string, File>(target.map(f => [key(f), f]));
        for (const f of incoming) map.set(key(f), f);
        return Array.from(map.values());
    }

    // ----- folder pick handlers -----
    onRagDirPicked(evt: Event) {
        const files = Array.from((evt.target as HTMLInputElement).files || []);
        this.ragFiles = files;
        this.ragFileCount = files.length;
        this.ragFolderName = this.folderNameFrom(files);
    }
    onFrameworkDirPicked(evt: Event) {
        const files = Array.from((evt.target as HTMLInputElement).files || []);
        this.frameworkFiles = files;
        this.frameworkFileCount = files.length;
        this.frameworkFolderName = this.folderNameFrom(files);
    }

    // ----- file pick handlers (individual files) -----
    onRagFilesPicked(evt: Event) {
        const files = Array.from((evt.target as HTMLInputElement).files || []);
        if (!files.length) return;
        this.ragFiles = this.mergeFiles(this.ragFiles, files);
        this.ragFileCount = this.ragFiles.length;
        if (!this.ragFolderName) this.ragFolderName = ''; // show "N file(s)"
    }
    onFrameworkFilesPicked(evt: Event) {
        const files = Array.from((evt.target as HTMLInputElement).files || []);
        if (!files.length) return;
        this.frameworkFiles = this.mergeFiles(this.frameworkFiles, files);
        this.frameworkFileCount = this.frameworkFiles.length;
        if (!this.frameworkFolderName) this.frameworkFolderName = '';
    }

    // ----- upload -----
    onUpload() {
        if (this.busy) return;
        if (this.isUploadInvalid) {
            alert('Pick at least one folder or file set to upload.');
            return;
        }
        this.busy = true;
        this.overallProgress = 0;
        this.lastError = null;

        const uploads: Array<Promise<void>> = [];

        if (this.ragFileCount > 0) {
            uploads.push(
                this.uploadDir('/api/rag/upload', this.ragFiles, 'RAG', this.ragFolderName, this.ragFileCount)
                    .catch(err => this.notifyUploadFailure('RAG', err))
            );
        }
        if (this.frameworkFileCount > 0) {
            uploads.push(
                this.uploadDir('/api/framework/upload', this.frameworkFiles, 'FRAMEWORK', this.frameworkFolderName, this.frameworkFileCount)
                    .catch(err => this.notifyUploadFailure('FRAMEWORK', err))
            );
        }

        Promise.allSettled(uploads).then(() => {
            const hasRunning = this.jobFeed.some(j => j.state === 'RUNNING');
            if (!hasRunning) {
                this.busy = false;
                this.overallProgress = 100;
            }
        });
    }

    private notifyUploadFailure(type: 'RAG' | 'FRAMEWORK', err: any) {
        const reason = this.humanizeErr(err);

        // show as banner
        this.lastError = `${type} upload failed: ${reason}`;

        // reflect in Jobs list
        const failed: JobStatus = {
            id: 'local-' + Date.now(),
            type,
            state: 'FAILED',
            message: reason,
            processed: 0,
            total: 0
        };
        this.jobFeed = [failed, ...this.jobFeed];

        // stop spinner for this phase
        this.busy = false;
        this.overallProgress = 0;
        console.error(`❌ ${type} upload failed:`, reason);
    }

    private async uploadDir(
        url: string,
        files: File[],
        type: 'RAG' | 'FRAMEWORK',
        folderName: string,
        _count: number
    ): Promise<void> {
        const form = new FormData();
        files.forEach(f => {
            const wf = f as File & { webkitRelativePath?: string };
            form.append('files', f, wf.webkitRelativePath || f.name);
        });
        form.append('folderName', folderName);

        // clear previous error on new attempt for this upload
        this.lastError = null;

        await new Promise<void>((resolve, reject) => {
            this.http.post<{ jobId: string }>(url, form, {
                reportProgress: true,
                observe: 'events'
            }).subscribe({
                next: (evt: HttpEvent<{ jobId: string }>) => {
                    if (evt.type === HttpEventType.UploadProgress && evt.total) {
                        this.bumpOverallDuringUpload();
                    } else if (evt.type === HttpEventType.Response && evt.body?.jobId) {
                        this.beginPolling(evt.body.jobId, type);
                        resolve();
                    }
                },
                error: (err) => reject(err)
            });
        });
    }

    private bumpOverallDuringUpload() {
        const hasJobs = this.jobFeed.length > 0;
        if (hasJobs) {
            this.overallProgress = this.computeOverallProgress();
        } else {
            this.overallProgress = Math.min(25, (this.overallProgress + 5));
        }
    }

    private computeOverallProgress(): number {
        if (this.jobFeed.length === 0) return 0;
        const sum = this.jobFeed.reduce((acc, j) => acc + this.progressOf(j), 0);
        return Math.round(sum / this.jobFeed.length);
    }

    private beginPolling(jobId: string, type: 'RAG' | 'FRAMEWORK') {
        const initial: JobStatus = { id: jobId, type, state: 'RUNNING', message: 'Queued', processed: 0, total: 0 };
        this.jobFeed = [initial, ...this.jobFeed];

        const sub = interval(1000).subscribe(() => {
            const path = type === 'RAG' ? `/rag/jobs/${jobId}` : `/framework/jobs/${jobId}`;
            this.http.get<JobStatus>(path).subscribe({
                next: (status) => {
                    this.updateJob(status);
                    this.overallProgress = this.computeOverallProgress();
                    if (this.jobFeed.every(j => j.state !== 'RUNNING')) {
                        this.busy = false;
                    }
                },
                error: () => {}
            });
        });
        this.pollers.set(jobId, sub);
    }

    private updateJob(status: JobStatus) {
        const idx = this.jobFeed.findIndex(j => j.id === status.id);
        const updated = [...this.jobFeed];
        if (idx >= 0) updated[idx] = status; else updated.unshift(status);
        this.jobFeed = updated;
        if (status.state === 'DONE' || status.state === 'FAILED') {
            this.pollers.get(status.id)?.unsubscribe();
            this.pollers.delete(status.id);
        }
    }

    progressOf(j: JobStatus): number {
        return j.total ? Math.round((j.processed / j.total) * 100) : (j.state === 'FAILED' ? 100 : 0);
    }

    badgeClass(j: JobStatus) {
        return j.state === 'DONE' ? 'ok' : j.state === 'FAILED' ? 'fail' : 'run';
    }

    humanizeErr(err: any): string {
        try {
            // Spring default error body: { timestamp, status, error, message, path }
            if (err?.error?.message) return err.error.message;
            if (typeof err?.error === 'string') return err.error; // HTML/text fallback
            if (err?.status && err?.statusText) return `${err.status} ${err.statusText}`;
            return err?.message ?? 'Unknown error';
        } catch { return 'Unknown error'; }
    }

    ngOnDestroy() {
        this.pollers.forEach(s => s.unsubscribe());
        this.pollers.clear();
    }
}
