import { Component, OnInit } from '@angular/core';
import { Track, Task } from './shared/Track.model';
import { CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import  *  as  data  from  './shared/data.json';
@Component({
  selector: 'app-api-playground',
  templateUrl: './api-playground.component.html',
  standalone: false
})
export class ApiPlaygroundComponent implements OnInit {
  ngOnInit(): void {
  }
  
  initialSkeleton = new Array(5);
  done = [
    'A','B','C','D','E'
  ];

  drop(event: CdkDragDrop<string[]>) {
    if (event.previousContainer === event.container) {
     moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      event.container.data[event.currentIndex] =  event.previousContainer.data[event.previousIndex];
    }

  }

}
