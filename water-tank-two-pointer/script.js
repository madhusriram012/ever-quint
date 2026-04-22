/*Two pointer approach to calculate trapped water in O(n) time and O(n) space for visualization
  or else in O(1) space if we only need the total amount.This is very much the same as Trapping rain water in leetcode which I have already solved*/
function trapWater(heights) {
  let left = 0, right = heights.length - 1;
  let leftMax = 0, rightMax = 0, total = 0;
  const water = new Array(heights.length).fill(0);

  while (left < right) {
    if (heights[left] < heights[right]) {
      leftMax = Math.max(leftMax, heights[left]);
      water[left] = leftMax - heights[left];
      total += water[left];
      left++;
    } else {
      rightMax = Math.max(rightMax, heights[right]);
      water[right] = rightMax - heights[right];
      total += water[right];
      right--;
    }
  }
  return { total, water };
}

function parseInput(str) {
  return str
    .replace(/[\[\]]/g, "")
    .split(",")
    .map(s => {
      const n = parseInt(s.trim(), 10);
      return isNaN(n) || n < 0 ? 0 : n;
    });
}

function renderSVG(heights, water) {
  const maxH = Math.max(...heights, 1);
  const cols = heights.length;
  const cell = 40;
  const pad = 1;
  const w = cols * cell;
  const h = maxH * cell;

  let rects = "";

  for (let col = 0; col < cols; col++) {
    const x = col * cell;

    // water cells
    for (let row = 0; row < water[col]; row++) {
      const y = (maxH - heights[col] - water[col] + row) * cell;
      rects += `<rect x="${x + pad}" y="${y + pad}" width="${cell - pad * 2}" height="${cell - pad * 2}" fill="#3da5f4" opacity="0.7" rx="2"/>`;
    }

    // block cells
    for (let row = 0; row < heights[col]; row++) {
      const y = (maxH - heights[col] + row) * cell;
      rects += `<rect x="${x + pad}" y="${y + pad}" width="${cell - pad * 2}" height="${cell - pad * 2}" fill="#e0e0e0" rx="2"/>`;
    }
  }

  return `<svg viewBox="0 0 ${w} ${h}" xmlns="http://www.w3.org/2000/svg">${rects}</svg>`;
}

function run() {
  const input = document.getElementById("input").value.trim();
  if (!input) return;

  const heights = parseInput(input);
  const { total, water } = trapWater(heights);

  const resultEl = document.getElementById("result");
  resultEl.innerHTML = `Trapped water: <span>${total} unit${total !== 1 ? "s" : ""}</span>`;
  resultEl.hidden = false;

  document.getElementById("viz").innerHTML = heights.length > 0 && Math.max(...heights) > 0
    ? renderSVG(heights, water)
    : "";
}

document.getElementById("compute").addEventListener("click", run);
document.getElementById("input").addEventListener("keydown", e => {
  if (e.key === "Enter") run();
});

run();
