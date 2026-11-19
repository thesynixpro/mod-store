# Aprax Mod Store launcher icon patch

Prepared for `thesynixpro/mod-store` (branch `main`).

## Files
- `app/src/main/res/drawable-nodpi/ic_launcher_art.png` — supplied Aprax Mod Store logo, scaled with safe padding for Android adaptive-icon masking.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — uses the new logo artwork as the adaptive icon foreground.

## Apply
Copy the files into the matching paths in the repository, replacing the existing
`app/src/main/res/drawable/ic_launcher_foreground.xml`.

The repository manifest already declares:
- `android:icon="@mipmap/ic_launcher"`
- `android:roundIcon="@mipmap/ic_launcher_round"`

The existing adaptive icon XML files already point to `@drawable/ic_launcher_foreground`,
so no manifest or adaptive-icon XML change is needed.

Then run the repository's Android GitHub Actions workflow to build the APK.
