// The SHARP: Verdict mark: a hairline target ring, a blade-thin line running at it, one red point.
const RED = '#E10600', INK = '#050505', WHITE = '#FFFFFF';
exports.RED = RED; exports.INK = INK; exports.WHITE = WHITE;
exports.markGroup = () => `
  <circle cx="64" cy="64" r="40" fill="none" stroke="${WHITE}" stroke-width="3"
          stroke-dasharray="46.8 16" transform="rotate(11.5 64 64)"/>
  <g stroke="${WHITE}" stroke-width="3" stroke-linecap="butt">
    <line x1="64" y1="8" x2="64" y2="30"/><line x1="64" y1="98" x2="64" y2="120"/>
    <line x1="8" y1="64" x2="30" y2="64"/><line x1="98" y1="64" x2="120" y2="64"/>
  </g>
  <line x1="12" y1="116" x2="54" y2="74" stroke="${WHITE}" stroke-width="3" stroke-linecap="butt"/>
  <circle cx="64" cy="64" r="6" fill="${RED}"/>`;
exports.markSvg = (bg) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="512" height="512">
  ${bg ? `<rect width="128" height="128" rx="14" fill="${INK}"/><rect x="1" y="1" width="126" height="126" rx="13" fill="none" stroke="#FFFFFF" stroke-opacity=".14"/>` : ''}${exports.markGroup()}
</svg>`;
