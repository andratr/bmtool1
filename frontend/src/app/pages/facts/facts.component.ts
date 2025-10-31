import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-carousel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './facts.component.html',
  styleUrls: ['./facts.component.css']
})


export class FactsComponent {
    slides = [
        {
            title: 'Data integrity first',
            text: 'Data mapping and quality are the top migration risks. Failures are prevented with early schema mapping, encoding checks, cleansing, and strict validations.',
            image: 'assets/images/markus-winkler-kA7zREkzrBw-unsplash.jpg'
        },
        {
            title: 'Downtime is optional',
            text: 'You can keep the system available by preparing the new version in parallel, moving users over gradually, and handing the database over in a planned step. This approach avoids one large shutdown.',
            image: 'assets/images/john-cardamone-3IUgJsPVDTI-unsplash.jpg'
        },
        {
            title: 'Dependencies drive complexity',
            text: 'Hidden integrations, such as cron jobs, third-party APIs, SSO, webhooks, often break first. Inventory them, set contract tests, and use test doubles where needed.',
            image: 'assets/images/growtika-ZfVyuV8l7WU-unsplash.jpg'
        },
        {
            title: 'Parallel runs de-risk go-live',
            text: 'Operate legacy and target systems side-by-side. Reconcile totals and event counts, enforce error budgets, and only cut over when results match.',
            image: 'assets/images/cova-software-yGg45DgysfQ-unsplash.jpg'
        },
        {
            title: 'Beyond lift-and-shift; set your cloud foundation',
            text: 'Migration spans data, apps, integrations, infra, and people/process—and lays groundwork for cloud: IaC, CI/CD, observability, security baselines, and FinOps.',
            image: 'assets/images/roy-wen-4HsgSS2Tq_4-unsplash.jpg'
        },
        {
            title: 'Legacy COBOL still matters',
            text: 'Significant core workloads in finance and the public sector remain on COBOL/mainframes; appropriate migration strategies include interface encapsulation, selective replatforming, and incremental replacement, rather than rewriting everything at once.',
            image: 'assets/images/daniel-de-nadai-7LePkqSqgEA-unsplash.jpg'
        }
    ];


// 🔥 wheel setup
  containerSize = 1600;
  offsetX = 870;
  offsetY = -400;

  get center(): number {
    return this.containerSize / 2;
  }
  get radius(): number {
    return this.center - 40;
  }

  angleStep = 360 / this.slides.length;
  currentRotation = 180; // start with slide 1 at left

    getPosition(index: number) {
        const angle = (index * this.angleStep + this.currentRotation) * (Math.PI / 180);
        return {
            x: this.center + this.radius * Math.cos(angle),
            y: this.center + (this.radius) * Math.sin(angle) // ← scaled Y
        };
    }




  /** 🔥 detect active index (leftmost, ~180°) */
  get activeIndex(): number {
    const n = this.slides.length;
    const step = this.angleStep;
    const TARGET = 180;
    const raw = (-TARGET - this.currentRotation) / step;
    const i = Math.round(raw);
    return ((i % n) + n) % n;
  }

  next() { this.currentRotation -= this.angleStep; }
  prev() { this.currentRotation += this.angleStep; }
}
