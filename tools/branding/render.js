const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { markSvg, markGroup, RED, INK, WHITE } = require('./mark.js');
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });

const grid = (w, h) => {
  let s = '';
  for (let x = 0; x <= w; x += 80) s += `<line x1="${x}" y1="0" x2="${x}" y2="${h}"/>`;
  for (let y = 0; y <= h; y += 80) s += `<line x1="0" y1="${y}" x2="${w}" y2="${y}"/>`;
  return s;
};

const banner = (w, h, big) => `<!doctype html><html><head><meta charset="utf-8"><style>
  html,body{margin:0;background:${INK}}
  .wrap{position:relative;width:${w}px;height:${h}px;overflow:hidden;color:${WHITE};
    background:radial-gradient(800px 420px at 85% 0%, rgba(255,255,255,.10), transparent 62%), ${INK};
    font-family:'Inter Display','Inter',system-ui,sans-serif}
  svg.grid{position:absolute;inset:0;opacity:.05;stroke:#fff;stroke-width:1}
  .mark{position:absolute;left:${big ? 96 : 72}px;top:50%;transform:translateY(-50%);width:${big ? 250 : 200}px;height:${big ? 250 : 200}px}
  .txt{position:absolute;left:${big ? 420 : 330}px;top:50%;transform:translateY(-50%)}
  .word{white-space:nowrap;line-height:1;font-size:${big ? 74 : 68}px;letter-spacing:.2em;text-transform:uppercase}
  .word .s{font-weight:800}
  .word .c{color:${RED};font-weight:300;margin:0 .22em 0 .05em}
  .word .v{font-weight:200;color:#e8e8e8}
  .hair{margin-top:${big ? 30 : 22}px;width:${big ? 560 : 470}px;height:1px;background:linear-gradient(90deg,#fff,rgba(255,255,255,0))}
  .tag{margin-top:${big ? 26 : 18}px;display:flex;align-items:center;gap:14px;font-weight:600;font-size:${big ? 24 : 18}px;letter-spacing:.42em;color:#d4d4d4;text-transform:uppercase}
  .tag i{display:inline-block;width:10px;height:10px;background:${RED}}
  .sub{margin-top:${big ? 16 : 12}px;font-weight:400;font-size:${big ? 21 : 16}px;color:#8a8a8a;letter-spacing:.06em}
  .rule{position:absolute;left:0;bottom:0;width:${big ? 220 : 160}px;height:5px;background:${RED}}
  .corner{position:absolute;width:28px;height:28px;border-color:rgba(255,255,255,.35);border-style:solid;border-width:0}
  .tl{left:28px;top:28px;border-top-width:1px;border-left-width:1px}
  .tr{right:28px;top:28px;border-top-width:1px;border-right-width:1px}
  .bl{left:28px;bottom:28px;border-bottom-width:1px;border-left-width:1px}
  .br{right:28px;bottom:28px;border-bottom-width:1px;border-right-width:1px}
</style></head><body><div class="wrap">
  <svg class="grid" width="${w}" height="${h}">${grid(w, h)}</svg>
  <div class="corner tl"></div><div class="corner tr"></div><div class="corner bl"></div><div class="corner br"></div>
  <svg class="mark" viewBox="0 0 128 128">${markGroup()}</svg>
  <div class="txt">
    <div class="word"><span class="s">Sharp</span><span class="c">:</span><span class="v">Verdict</span></div>
    <div class="hair"></div>
    <div class="tag"><i></i>Target Lock</div>
    <div class="sub">One target. Decided once. Held until you let go.</div>
  </div>
  <div class="rule"></div>
</div></body></html>`;

(async () => {
  fs.writeFileSync(path.join(out, 'logo.svg'), markSvg(true));
  fs.writeFileSync(path.join(out, 'mark.svg'), markSvg(false));
  const browser = await chromium.launch();
  const shot = async (html, w, h, file) => {
    const page = await browser.newPage({ viewport: { width: w, height: h } });
    await page.setContent(html, { waitUntil: 'load' });
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(out, file), omitBackground: true });
    await page.close();
  };
  const bare = (svg, px) => `<!doctype html><html><body style="margin:0;background:transparent">${svg.replace('width="512" height="512"', `width="${px}" height="${px}"`)}</body></html>`;
  await shot(bare(markSvg(true), 128), 128, 128, 'icon.png');
  await shot(bare(markSvg(true), 512), 512, 512, 'icon-512.png');
  await shot(banner(1280, 360, false), 1280, 360, 'banner.png');
  await shot(banner(1280, 640, true), 1280, 640, 'social-preview.png');
  await browser.close();
  console.log('ok', fs.readdirSync(out).join(' '));
})();
