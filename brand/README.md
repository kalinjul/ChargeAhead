# Brand assets

Source material for the PowerTrip brand, as delivered. The app does not read
these files; the drawables were generated from them and are committed. When
a file here changes, regenerate the drawable, re-record the goldens
(`tools/screenshots.sh update`) and look at them.

| source | becomes |
|---|---|
| `svg/powertrip_icon.svg` | `phone-ui/.../drawable{,-night}/ic_powertrip.xml` (tight crop, brand purple / light purple), `phone-ui/.../drawable/splash_icon.xml` (same mark, centred so its diagonal fits the inner two thirds of the splash icon slot) |
| `svg/powertrip_text_lowercase_twotone.svg` | `phone-ui/.../drawable/logo_powertrip_text.xml` |
| `svg/powertrip_text_lowercase_twotone_dark-bg.svg` | `phone-ui/.../drawable-night/logo_powertrip_text.xml`, `phone-ui/.../drawable/splash_branding.xml` (200×63dp, the branding slot does not keep aspect ratio) |
| `svg/powertrip_logo_lowercase_twotone_dark-bg.svg` | `phone-ui/.../drawable/logo_powertrip_stacked_dark.xml`, which only feeds the `CarPaneLogoOn*` previews; `tools/pane-logo.py` turns those two goldens into `drawable-nodpi/logo_powertrip_stacked.png`, the car's detail pane image (the host mangles the vector) |
| `android/res/` | `androidApp/src/main/res/` launcher package, copied as is |

Everything else (camelcase, stacked logos, white/black variants, PNG renders,
Play Store icon) is reference only.
