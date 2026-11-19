# Aprax Mods Store

A transparent Android marketplace aggregator for modded apps, built with Kotlin, Jetpack Compose, MVVM, Clean Architecture, Room, Hilt and WorkManager.

It reads listings from public source sites (Modyolo, HappyMod, A1N), attributes every card to the site that published it, downloads APKs only over HTTPS directly from the source, and never installs anything silently — the user always confirms through Android's own installer dialog.

## Principles

- **No fabricated data.** The app never synthesises a listing, download URL, rating or download count. Source sites are the origin of every fact shown.
- **Source attribution.** Every listing card names the site it came from. "Open original source" goes to the site's own page.
- **No silent installs.** `REQUEST_INSTALL_PACKAGES` only lets the app *ask*; Android's confirmation dialog is never bypassed.
- **HTTPS only.** Cleartext HTTP is disabled network-wide; an APK must never arrive over an unencrypted connection.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Shell, navigation graph, `ApraxApplication`, deep-link entry |
| `core` | Design system, shared UI states, external-link handling |
| `domain` | Models, repository contracts, use cases — pure Kotlin |
| `data` | Room cache, DataStore settings, source parsers, downloads, installs, Hilt wiring |
| `feature-home` | Newest listings across enabled sources |
| `feature-search` | Full-text search with history |
| `feature-categories` | Category taxonomy browser |
| `feature-details` | Listing details, download, favourites/save |
| `feature-downloads` | Active downloads queue and history |
| `feature-library` | Favourites and saved-for-later |
| `feature-settings` | Appearance, download policy, sources, legal |

## Building

Requirements: JDK 17, Android SDK with `compileSdk 37`.

```bash
./gradlew :app:assembleDebug
```

The GitHub Actions workflow (`assembleDebug`) publishes the resulting APK to the repo's **latest** release on every push to `main`.

## Deep links

`https://modyolo.com/<slug>.html` opens the listing's details directly in the app; all other source URLs open in the system browser.

## Legal

Aprax Mods Store is an independent index. It does not host, mirror or modify any APK. Listings and downloads belong to the source sites named on each card.