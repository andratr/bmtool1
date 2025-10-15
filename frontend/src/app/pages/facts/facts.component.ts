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
      title: 'Slide 1',
      text: 'This is the first slide description.',
      image: 'https://picsum.photos/200/120?random=1'
    },
    {
      title: 'Slide 2',
      text: 'Here is another card with some info.',
      image: 'https://picsum.photos/200/120?random=2'
    },
    {
      title: 'Slide 3',
      text: 'More content for the carousel!',
      image: 'https://picsum.photos/200/120?random=3'
    },
    {
      title: 'Slide 4',
      text: 'Last one in the sample list.',
      image: 'https://picsum.photos/200/120?random=4'
    },
    {
      title: 'Slide 5',
      text: 'Another slide goes here.',
      image: 'https://picsum.photos/200/120?random=5'
    },
    {
      title: 'Slide 6',
      text: 'Keep spinning through the wheel.',
      image: 'https://picsum.photos/200/120?random=6'
    },
    {
      title: 'Slide 7',
      text: 'Almost at the end!',
      image: 'https://picsum.photos/200/120?random=7'
    },
    {
      title: 'Slide 8',
      text: 'Final slide in the set.',
      image: 'https://picsum.photos/200/120?random=8'
    }
  ];

  // 🔥 wheel setup
  containerSize = 1200;
  offsetX = 1000;
  offsetY = -200;

  get center(): number {
    return this.containerSize / 2;
  }
  get radius(): number {
    return this.center - 40;
  }

  angleStep = 360 / this.slides.length;
  currentRotation = 180; // start with slide 1 at left

  getPosition(index: number) {
    const angle = -(index * this.angleStep + this.currentRotation) * (Math.PI / 180);
    return {
      x: this.center + this.radius * Math.cos(angle) - 400, // half of width (800/2)
      y: this.center + this.radius * Math.sin(angle) - 150  // half of height (300/2)
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
