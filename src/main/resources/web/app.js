const statusEl = document.getElementById('status');
const playersEl = document.getElementById('players');
const canvas = document.getElementById('tileCanvas');
const ctx = canvas.getContext('2d');

// Simple tile fetcher (z=2 view over a 3x2 tile grid)
const z = 2;
const widthTiles = 3;
const heightTiles = 2;

function drawTiles() {
  const tileSize = 256;
  for (let ty = 0; ty < heightTiles; ty++) {
    for (let tx = 0; tx < widthTiles; tx++) {
      const url = `/api/tile/${z}/${tx}/${ty}.png`;
      const img = new Image();
      img.onload = () => {
        ctx.drawImage(img, tx * tileSize, ty * tileSize);
      };
      img.src = url;
    }
  }
}

drawTiles();

function updatePlayers(players) {
  playersEl.innerHTML = '';
  for (const p of players) {
    const li = document.createElement('li');
    li.innerHTML = `<strong>${p.name}</strong><div class="dim">${p.dimension}</div><div>x:${p.x.toFixed(1)} y:${p.y.toFixed(1)} z:${p.z.toFixed(1)}</div>`;
    playersEl.appendChild(li);
  }
  // draw blips on canvas (simple projection for demo)
  ctx.fillStyle = 'rgba(255, 80, 80, 0.9)';
  for (const p of players) {
    const px = ((p.x % 1024) + 1024) % 1024; // wrap
    const pz = ((p.z % 512) + 512) % 512;
    ctx.beginPath();
    ctx.arc(px * (canvas.width/1024), pz * (canvas.height/512), 4, 0, Math.PI*2);
    ctx.fill();
  }
}

function connectEvents() {
  try {
    const ev = new EventSource('/events');
    ev.onopen = () => statusEl.textContent = 'Connected';
    ev.onerror = () => statusEl.textContent = 'Disconnected';
    ev.onmessage = (e) => {
      try {
        const payload = JSON.parse(e.data);
        if (payload && payload.players) {
          // redraw tiles lightly to clear blips
          drawTiles();
          updatePlayers(payload.players);
        }
      } catch (err) {
        console.error('Bad event payload', err);
      }
    };
  } catch (err) {
    statusEl.textContent = 'Event stream failed';
  }
}

connectEvents();
