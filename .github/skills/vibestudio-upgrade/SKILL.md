---
name: vibestudio-upgrade
description: Cut a new VibeStudio release. Use when the user invokes "vibestudio-upgrade <version tag>" (e.g. "vibestudio-upgrade v0.1.7") or asks to release/bump/tag a new VibeStudio version. Bumps versionName/versionCode, writes the CHANGELOG section, commits, tags with the required `v` prefix, pushes, and verifies the Release workflow.
---

# vibestudio-upgrade `<version tag>`

Argument: the version tag, e.g. `v0.1.7` or `0.1.7` (prerelease suffix allowed: `v0.2.0-beta1`).
If missing, ask the user for it.

## Rules (from `.github/workflows/release.yml` and README "Cutting a release")
- The release workflow triggers ONLY on tags matching `v*`. Always normalize to `vX.Y.Z[-suffix]`; `VERSION` = tag without the `v`, `BASE` = VERSION without any `-suffix`.
- The workflow fails if `BASE` != `versionName` in `app/build.gradle`.
- The release notes come from the `## [BASE]` section of `CHANGELOG.md`.
- Releases are **immutable**: a tag name that was ever released can never be pushed again. If a release build fails after the release was created, bump to a new patch version instead of re-tagging.
- Tag only a commit that is on `main` and already pushed.

## Steps
1. **Preflight**
   - `git fetch --tags origin`; be on `main`, up to date with `origin/main`, with a clean tracked tree (untracked files such as `libtermux-android/build-logic/.kotlin/` are fine; don't commit them).
   - Verify the tag does not exist locally/remotely (`git ls-remote --tags origin "v<VERSION>"`) and that no GitHub release exists (`gh release view v<VERSION>`). Abort and report if it does.
   - Verify the new `BASE` is greater than the current `versionName`.
2. **Bump** `app/build.gradle`: `versionName "<BASE>"` and `versionCode` + 1.
3. **Changelog**: collect changes since the previous tag (`git log --format='%h %s' <prev-tag>..HEAD`, plus `gh pr list --state merged` titles). Insert a `## [<BASE>]` section above the previous one in `CHANGELOG.md`, using Keep a Changelog headings (`### Added` / `### Changed` / `### Fixed`) and the existing bold-title bullet style. For a prerelease reuse the same BASE section.
4. **Commit & push** to `main`:
   ```bash
   git add app/build.gradle CHANGELOG.md
   git commit -m "release: <VERSION>" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
   git push origin main
   ```
   If `main` is protected and the push is rejected, open a PR for the bump, wait for it to merge, then continue from the merge commit.
5. **Tag & push**: `git tag v<VERSION> && git push origin v<VERSION>`.
6. **Verify**: `gh run list --workflow Release --limit 3`, then `gh run watch <id> --exit-status` (build takes ~4 min). On success confirm with `gh release view v<VERSION> --json assets -q '.assets[].name'` — expect `VibeStudio-<VERSION>-aarch64.apk` and its `.sha256` (the architecture suffix is set by `ARCH` in release.yml). On failure show `gh run view <id> --log-failed`, diagnose, and report; do not re-push the tag.
7. Report the release URL (`gh release view v<VERSION> --json url -q .url`).
