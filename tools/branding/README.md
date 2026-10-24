# Branding generator

Regenerates the logo, mod icon, README banner and social preview in `assets/branding/` and the mod icon in `src/main/resources/assets/sharp/icon.png`.

```bash
cd tools/branding
npm install playwright          # once
node render.js ../../assets/branding
cp ../../assets/branding/icon.png ../../src/main/resources/assets/sharp/icon.png
```

It needs the Inter Display font installed. The palette is black `#050505`, white, and one red `#E10600`, used only as the single danger accent. Edit `mark.js` for the mark and `render.js` for the banner layout.
