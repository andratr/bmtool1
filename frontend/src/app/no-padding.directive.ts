import {Directive, ElementRef, Rendere2} from '@angular/core';

@Directive({
  selector:'[appNoPadding]',
  standalone:true
})

export class NoPaddingDirective{
  constructor(el:ElementRef, r:Rendere2){
    r.setStyle(el.nativeElement, 'padding', '0');
  }
}
