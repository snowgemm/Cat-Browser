# Cat Browser Macrobenchmarks

These benchmarks measure the production-like Android app variant on a physical device.

## Requirements

- Android 10 (API 29) or newer.
- A physical Android device with USB debugging enabled.
- The device must remain awake, unlocked, and sufficiently charged during the run.
- Install the Android SDK components configured by the project.

Emulators are intentionally not the performance baseline: their frame timing does not represent a user's physical device.

## Build

```bash
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

## Run on a connected device

```bash
./gradlew :macrobenchmark:connectedCheck
```

The suite currently measures:
- Cold app startup using `StartupTimingMetric`.
- Tab Switcher grid scrolling with eight tabs using `FrameTimingMetric`.

The scrolling benchmark clears Cat Browser's app data during setup so each iteration starts from a predictable tab state. Do not run it against an installation containing data you need to keep.

## Results

Macrobenchmark writes JSON metrics and Perfetto traces under:

```
macrobenchmark/build/outputs/connected_android_test_additional_output/
```

Compare repeated runs on the same physical device, Android version, refresh-rate setting, and thermal state. Do not infer a universal FPS target from a single run.
