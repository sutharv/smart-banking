import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <div class="sim-band" role="note">
      Simulator — all accounts, people and money here are fictional. Fraud rules are illustrative only.
    </div>
    <router-outlet />
  `,
})
export class App {}
