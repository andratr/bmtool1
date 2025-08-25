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
    { title: 'Slide 1' },
    { title: 'Slide 2' },
    { title: 'Slide 3' },
    { title: 'Slide 4' },
    { title: 'Slide 5' },
    { title: 'Slide 6' },
    { title: 'Slide 7' },
    { title: 'Slide 8' }
  ];

  // 🔥 size
  containerSize = 1200;

  // 🔥 position offsets
  offsetX = 1000;  // px to the right
  offsetY = -200;   // px down

  get center(): number {
    return this.containerSize / 2;
  }

  get radius(): number {
    return this.center - 40;
  }

  angleStep = 360 / this.slides.length;
  currentRotation = 0;

  getPosition(index: number) {
    const angle = (index * this.angleStep + this.currentRotation) * (Math.PI / 180);
    return {
      x: this.center + this.radius * Math.cos(angle) - 30,
      y: this.center + this.radius * Math.sin(angle) - 30
    };
  }

  next() { this.currentRotation += this.angleStep; }
  prev() { this.currentRotation -= this.angleStep; }
}
