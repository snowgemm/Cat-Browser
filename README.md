# Cat Browser

Android browser focused on **extension support** and the ability to extend with **Rust**.

Built on top of [Iceraven](https://github.com/fork-maintainers/iceraven-browser) (Firefox for Android / Fenix + GeckoView).

## Goals

- Strong WebExtensions support (including sideloading `.xpi`)
- Clean, independent app identity (own package name so it can be installed alongside Firefox / Iceraven)
- Room to add custom components written in Rust
- Lightweight branding layer on top of upstream

## Current Status

This repository currently contains:

- GitHub Actions workflow that builds an APK from a chosen Iceraven tag
- Branding script that turns the build into a separate "Cat Browser" app

## Building

1. Go to the **Actions** tab
2. Run the **Build Cat Browser APK** workflow
3. Choose an Iceraven tag (default: `iceraven-2.46.0`)
4. Download the artifact when finished

## Branding

The script `scripts/cat-branding.sh` is automatically applied during the CI build. It:

- Changes the application ID so the app is independent
- Renames visible "Iceraven" strings to "Cat Browser"
- Updates deep link scheme and shared user ID

You can also run it locally after cloning Iceraven:

```bash
bash scripts/cat-branding.sh /path/to/iceraven
```

## Next Steps

- [ ] Custom launcher icon
- [ ] Default extension collection
- [ ] Optional Rust modules (via JNI or GeckoView customization)
- [ ] Vietnamese localization improvements

## License

Upstream Iceraven / Firefox for Android is under the Mozilla Public License 2.0.
