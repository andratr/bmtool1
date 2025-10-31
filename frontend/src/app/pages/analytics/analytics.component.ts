import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-analytics',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './analytics.component.html',
    styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent {
    // mock data
    cards = [
        { label: 'Ingested Pairs', value: 1280 },
        { label: 'Framework Files', value: 342 },
        { label: 'Jobs (Running)', value: 2 },
        { label: 'Jobs (Failed, 24h)', value: 1 },
    ];

    topQueries = [
        { q: 'customer_refund', hits: 42 },
        { q: 'plsql package order_v2', hits: 28 },
        { q: 'payment retry', hits: 25 },
        { q: 'shipment tracking', hits: 18 },
    ];

    lastUpdated = new Date();
}
