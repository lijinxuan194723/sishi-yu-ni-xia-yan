# Review layout correction

The 320 px / 130% typography regression exposed the inherited workday badge border: the 8 px line box plus 2 px of border overflowed its 8 px grid track. The supplied-artwork final stylesheet now uses an inset outline inside the badge bounds. Narrow calendar numerals are 12 px so their glyphs fit the square cell's reserved middle track. Chat correction actions also explicitly override the legacy global hiding rule.

Keep the browser overlap assertions unchanged. The next independent review build reruns them against the actual bundled CSS, then tests the same APK in the Android emulator. This is an independent review channel, not an in-place update to 2.0.4.
