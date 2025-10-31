import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
    selector: 'app-documentation',
    templateUrl: './documentation.component.html',
    styleUrls: ['./documentation.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentationComponent {
    /**
     * Optional: pass a measured CO₂ value (in grams) to show live equivalents.
     * If undefined, the template can still render the static docs.
     */
    @Input() gramsCO2?: number;

    // Tunable defaults (should mirror your backend CarbonEstimator)
    readonly GRID_G_PER_KWH = 400;   // global-average placeholder
    readonly DEFAULT_PUE = 1.2;

    // Equivalence constants
    readonly TREE_ABSORB_PER_YEAR_G = 22_000; // ~22 kg CO2/year per mature tree
    readonly CAR_G_PER_KM = 120;              // petrol car
    readonly AIR_G_PER_PAX_KM = 150;          // short-haul flights
    readonly PHONE_CHARGE_G = 6;              // per full charge (midpoint)

    get treesNeeded(): number | null {
        if (!this.isFinite(this.gramsCO2)) return null;
        return (this.gramsCO2 as number) / this.TREE_ABSORB_PER_YEAR_G;
    }

    get carKm(): number | null {
        if (!this.isFinite(this.gramsCO2)) return null;
        return (this.gramsCO2 as number) / this.CAR_G_PER_KM;
    }

    get airKm(): number | null {
        if (!this.isFinite(this.gramsCO2)) return null;
        return (this.gramsCO2 as number) / this.AIR_G_PER_PAX_KM;
    }

    get phoneCharges(): number | null {
        if (!this.isFinite(this.gramsCO2)) return null;
        return (this.gramsCO2 as number) / this.PHONE_CHARGE_G;
    }

    format(n: number | null | undefined, digits = 2): string {
        if (!this.isFinite(n)) return '—';
        const v = n as number;
        // Use compact formatting for large numbers, e.g., 12,300 → 12.3K
        if (Math.abs(v) >= 10_000) {
            return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(v);
        }
        return new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(v);
    }

    private isFinite(v: unknown): v is number {
        return typeof v === 'number' && Number.isFinite(v);
    }
}
