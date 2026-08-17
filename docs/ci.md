# CI / CD

GitHub Actions workflows live under `.github/workflows/`.

## Continuous Integration (`ci.yml`)

Runs on pushes and pull requests:

| Job | What it does |
|---|---|
| **Backend** | Java 21 + `./mvnw test` and `package` |
| **Web** | Node 22 + `npm ci` + `npm run build` (uploads `web/dist`) |
| **Mobile** | Flutter stable: `analyze`, `test`, debug APK smoke build |

## Releases (`release.yml`)

Triggered by:

- pushing a version tag: `git tag v0.2.0 && git push origin v0.2.0`
- or **Actions → Release → Run workflow** (manual version input)

Produces a GitHub Release with:

| Artifact | Notes |
|---|---|
| Backend JAR | Spring Boot fat JAR |
| Web `student-league-web-<ver>.tar.gz` | Vite production `dist/` |
| Android APK + AAB | Flutter `--release` (default debug keystore until you add signing secrets) |
| iOS unsigned zip | `Runner.app` from `macos-latest` (`--no-codesign`); optional / non-blocking |

### Android signing (Store-ready)

Add repository secrets and extend `android-release` when ready:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Then decode the keystore in CI and pass `-P` / `key.properties` to Gradle before `flutter build appbundle --release`.

### iOS signing (App Store)

Requires Apple certificates, provisioning profiles, and preferably [Fastlane Match](https://docs.fastlane.tools/actions/match/). The current job only validates that the iOS target compiles without codesign.

## Example release flow

```bash
# ensure CI is green on main
git tag v0.1.0
git push origin v0.1.0
# GitHub Actions → Release workflow uploads assets to the GitHub Release
```
