# QueueWatch Forecast App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separately installable QueueWatch Forecast Android variant from the latest QueueWatch codebase, with a new testable hybrid ETA engine, confidence/range output, no forecast alerts, and no regression to the existing tracking flow.

**Architecture:** Keep one Android project and one source tree. Use product flavors to produce the stable QueueWatch package and a separate Forecast package; extract Forecast V1 into pure Kotlin components that consume live movement estimates plus a versioned compact historical baseline artifact. The foreground service remains the source of truth for tracking; forecast output is advisory and persisted separately so failures cannot affect IN_QUEUE/CALLED/UNKNOWN handling.

**Tech Stack:** Android 16 target / compileSdk 36, Kotlin 2.2.20, Jetpack Compose, foreground service, SharedPreferences for current tracking state, JUnit 4 for pure Kotlin unit tests, GitHub Actions/Gradle.

**Spec:** `docs/superpowers/specs/2026-09-24-queuewatch-forecast-design.md`

## Global Constraints

- Stable package remains exactly `com.pylikv.queuewatch`.
- Forecast package is exactly `com.pylikv.queuewatch.forecast`.
- Forecast app display name is exactly `QueueWatch Forecast`.
- Both packages must install simultaneously and have isolated Android app data.
- Always build Forecast from the latest QueueWatch source; never restore an older source snapshot to regain a feature.
- Existing position-threshold alert and confirmed-CALLED alert behavior must not depend on forecast results.
- Forecast V1 has no forecast notification/alarm.
- Forecast must return “insufficient data” instead of fabricating an ETA.
- Raw registration number is not part of forecast telemetry or historical baseline data.
- A telemetry/network failure must never stop the tracking foreground service.
- Existing CI-applied fixes are part of the current shipped behavior and must be materialized into source before Forecast work continues.

## Review Focus

- CI/source drift: the repository source must match the behavior produced today after the four Python patch scripts.
- Batch queue movement: a mass renumbering event must not be counted as hundreds of independent speed samples.
- Stale/no movement: after the live window expires, confidence must drop and the model must fall back to history or no forecast.
- Vehicle type separation: truck/car/bus/motorcycle counts and baseline keys must never be mixed.
- Process death/reboot: a restored tracking session must not display a stale forecast from another car/checkpoint/session.

---

### Task 1: Materialize the current shipped fixes into source

**Files:**
- Modify: `app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt`
- Modify: `app/src/main/java/com/pylikv/queuewatch/MainActivity.kt`
- Modify: `.github/workflows/android.yml`
- Keep temporarily for comparison, then delete after source verification:
  - `tools/apply_queue_type_count_fix.py`
  - `tools/apply_swipe_navigation_fix.py`
  - `tools/apply_call_progress_fix.py`
  - `tools/apply_queue_movement_info.py`

**Interfaces:**
- Consumes: the four current CI patch scripts.
- Produces: source code that already contains every behavior currently injected at CI time; subsequent tasks operate on this source directly.

- [ ] **Step 1: Add a source-state regression check before changing CI**

Create `tools/verify_materialized_fixes.py` with checks for the exact required markers:

```python
from pathlib import Path

service = Path("app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt").read_text()
main = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt").read_text()

required_service = [
    "sameTypeLiveQueueCount",
    "while (preferences.getBoolean(KEY_TRACKING_ACTIVE, false))",
]
required_main = [
    "BackHandler(",
    "detectHorizontalDragGestures(",
    "val callProgress =",
    'movement_session_id',
    "val positionsPassed =",
]

missing = [m for m in required_service if m not in service]
missing += [m for m in required_main if m not in main]

if missing:
    raise SystemExit("Missing materialized shipped fixes: " + ", ".join(missing))

print("Materialized shipped fixes verified")
```

- [ ] **Step 2: Run the check and verify it fails on the unmaterialized branch**

Run:

```bash
python3 tools/verify_materialized_fixes.py
```

Expected: FAIL listing markers that only the CI patches currently inject.

- [ ] **Step 3: Apply the four patch scripts once to the branch source**

Run, in current CI order:

```bash
python3 tools/apply_queue_type_count_fix.py
python3 tools/apply_swipe_navigation_fix.py
python3 tools/apply_call_progress_fix.py
python3 tools/apply_queue_movement_info.py
```

Then commit the resulting Kotlin source changes. Do not hand-copy an older file.

- [ ] **Step 4: Verify source now contains the shipped behavior**

Run:

```bash
python3 tools/verify_materialized_fixes.py
```

Expected: PASS.

- [ ] **Step 5: Remove patch execution from CI and run the verifier instead**

Replace the four “Apply …” workflow steps with:

```yaml
      - name: Verify shipped fixes are materialized
        run: python3 tools/verify_materialized_fixes.py
```

Delete the four apply scripts only after the verifier passes and a local/CI build succeeds.

- [ ] **Step 6: Build current QueueWatch before adding Forecast**

Run:

```bash
gradle assembleDebug
```

Expected: stable debug APK builds successfully with the materialized source.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt \
        app/src/main/java/com/pylikv/queuewatch/MainActivity.kt \
        .github/workflows/android.yml tools/
git commit -m "refactor: materialize shipped QueueWatch fixes"
```

---

### Task 2: Add stable and Forecast product flavors

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/stable/res/values/strings.xml`
- Create: `app/src/forecast/res/values/strings.xml`
- Create: `app/src/forecast/res/values/bools.xml`
- Create: `app/src/stable/res/values/bools.xml`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: one shared source tree.
- Produces:
  - stable variant package `com.pylikv.queuewatch`
  - forecast variant package `com.pylikv.queuewatch.forecast`
  - resource flag `R.bool.forecast_enabled`

- [ ] **Step 1: Add flavor configuration**

In `app/build.gradle.kts` add:

```kotlin
android {
    // existing config

    flavorDimensions += "mode"

    productFlavors {
        create("stable") {
            dimension = "mode"
            applicationId = "com.pylikv.queuewatch"
        }

        create("forecast") {
            dimension = "mode"
            applicationId = "com.pylikv.queuewatch.forecast"
            versionNameSuffix = "-forecast"
        }
    }
}
```

Remove the `applicationId` from `defaultConfig` so the flavor owns it.

- [ ] **Step 2: Make the manifest label flavor-driven**

Change:

```xml
android:label="QueueWatch"
```

to:

```xml
android:label="@string/app_name"
```

- [ ] **Step 3: Add flavor resources**

`app/src/stable/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">QueueWatch</string>
</resources>
```

`app/src/forecast/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">QueueWatch Forecast</string>
</resources>
```

`app/src/stable/res/values/bools.xml`:

```xml
<resources>
    <bool name="forecast_enabled">false</bool>
</resources>
```

`app/src/forecast/res/values/bools.xml`:

```xml
<resources>
    <bool name="forecast_enabled">true</bool>
</resources>
```

- [ ] **Step 4: Build both packages**

Run:

```bash
gradle assembleStableDebug assembleForecastDebug
```

Expected: two APKs with distinct application IDs.

- [ ] **Step 5: Verify package IDs**

Run Android build artifact inspection:

```bash
apkanalyzer manifest application-id app/build/outputs/apk/stable/debug/app-stable-debug.apk
apkanalyzer manifest application-id app/build/outputs/apk/forecast/debug/app-forecast-debug.apk
```

Expected:

```text
com.pylikv.queuewatch
com.pylikv.queuewatch.forecast
```

- [ ] **Step 6: Update CI artifact names**

Build and upload both debug APKs:

```yaml
      - name: Build stable and Forecast debug APKs
        run: gradle assembleStableDebug assembleForecastDebug

      - name: Upload stable APK
        uses: actions/upload-artifact@v4
        with:
          name: QueueWatch-stable-debug
          path: app/build/outputs/apk/stable/debug/app-stable-debug.apk

      - name: Upload Forecast APK
        uses: actions/upload-artifact@v4
        with:
          name: QueueWatch-Forecast-debug
          path: app/build/outputs/apk/forecast/debug/app-forecast-debug.apk
```

For Play release, keep the stable AAB as the production artifact unless a separate Forecast Play track is explicitly added later.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/stable app/src/forecast .github/workflows/android.yml
git commit -m "build: add separately installable Forecast flavor"
```

---

### Task 3: Add pure Forecast V1 domain model and hybrid engine

**Files:**
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastModels.kt`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastEngineV1.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/ForecastEngineV1Test.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes:
  - `ForecastInput(currentPosition, queueCount, historical, live, nowMillis)`
- Produces:
  - `ForecastResult.Available(etaMinutes, lowMinutes, highMinutes, confidence, effectivePositionsPerHour, algorithmVersion)`
  - `ForecastResult.Unavailable(reason)`

Add JUnit:

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

Define models:

```kotlin
package com.pylikv.queuewatch.forecast

enum class ForecastConfidence { LOW, MEDIUM, HIGH }

data class SpeedEstimate(
    val positionsPerHour: Double,
    val sampleCount: Int,
    val newestSampleAtMillis: Long
)

data class HistoricalEstimate(
    val positionsPerHour: Double,
    val sampleCount: Int,
    val absoluteErrorP50Minutes: Double,
    val absoluteErrorP80Minutes: Double
)

data class ForecastInput(
    val currentPosition: Int,
    val queueCount: Int,
    val historical: HistoricalEstimate?,
    val live: SpeedEstimate?,
    val nowMillis: Long
)

sealed interface ForecastResult {
    data class Available(
        val etaMinutes: Double,
        val lowMinutes: Double,
        val highMinutes: Double,
        val confidence: ForecastConfidence,
        val effectivePositionsPerHour: Double,
        val algorithmVersion: String = "v1"
    ) : ForecastResult

    data class Unavailable(val reason: String) : ForecastResult
}
```

- [ ] **Step 1: Write tests for required behavior**

`ForecastEngineV1Test.kt` must include at least:

```kotlin
@Test
fun noSpeedSourcesReturnsUnavailable() {
    val result = ForecastEngineV1.estimate(
        ForecastInput(
            currentPosition = 50,
            queueCount = 100,
            historical = null,
            live = null,
            nowMillis = 1_000_000L
        )
    )
    assertTrue(result is ForecastResult.Unavailable)
}

@Test
fun positionOneProducesNearZeroEta() {
    val result = ForecastEngineV1.estimate(
        ForecastInput(
            currentPosition = 1,
            queueCount = 100,
            historical = HistoricalEstimate(20.0, 100, 20.0, 40.0),
            live = null,
            nowMillis = 1_000_000L
        )
    ) as ForecastResult.Available
    assertEquals(0.0, result.etaMinutes, 0.001)
}

@Test
fun oneFreshLiveSampleCannotDominateHistory() {
    val result = ForecastEngineV1.estimate(
        ForecastInput(
            currentPosition = 101,
            queueCount = 150,
            historical = HistoricalEstimate(20.0, 100, 25.0, 50.0),
            live = SpeedEstimate(80.0, 1, 999_000L),
            nowMillis = 1_000_000L
        )
    ) as ForecastResult.Available
    assertTrue(result.effectivePositionsPerHour < 40.0)
}

@Test
fun staleLiveEstimateIsIgnored() {
    val result = ForecastEngineV1.estimate(
        ForecastInput(
            currentPosition = 61,
            queueCount = 100,
            historical = HistoricalEstimate(20.0, 100, 20.0, 45.0),
            live = SpeedEstimate(100.0, 10, 1_000L),
            nowMillis = 4_000_000L
        )
    ) as ForecastResult.Available
    assertEquals(20.0, result.effectivePositionsPerHour, 0.001)
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
gradle testForecastDebugUnitTest --tests "com.pylikv.queuewatch.forecast.ForecastEngineV1Test"
```

Expected: FAIL because `ForecastEngineV1` does not exist.

- [ ] **Step 3: Implement deterministic hybrid weighting**

Implement these V1 rules in `ForecastEngineV1.kt`:

```kotlin
object ForecastEngineV1 {
    private const val LIVE_MAX_AGE_MS = 60 * 60 * 1000L
    private const val MAX_LIVE_WEIGHT = 0.55
    private const val MIN_RATE = 0.25
    private const val MAX_RATE = 200.0

    fun estimate(input: ForecastInput): ForecastResult {
        val remaining = (input.currentPosition - 1).coerceAtLeast(0)
        if (remaining == 0) {
            return ForecastResult.Available(
                etaMinutes = 0.0,
                lowMinutes = 0.0,
                highMinutes = 0.0,
                confidence = ForecastConfidence.HIGH,
                effectivePositionsPerHour = input.historical?.positionsPerHour
                    ?: input.live?.positionsPerHour
                    ?: 0.0
            )
        }

        val historicalRate = input.historical?.positionsPerHour
            ?.takeIf { it in MIN_RATE..MAX_RATE }

        val liveFresh = input.live?.takeIf {
            it.positionsPerHour in MIN_RATE..MAX_RATE &&
                input.nowMillis - it.newestSampleAtMillis <= LIVE_MAX_AGE_MS
        }

        if (historicalRate == null && liveFresh == null) {
            return ForecastResult.Unavailable("insufficient_speed_data")
        }

        val liveWeight = when {
            liveFresh == null -> 0.0
            historicalRate == null -> 1.0
            liveFresh.sampleCount < 3 -> 0.15
            liveFresh.sampleCount < 6 -> 0.30
            else -> MAX_LIVE_WEIGHT
        }

        val effective = when {
            historicalRate == null -> liveFresh!!.positionsPerHour
            liveFresh == null -> historicalRate
            else -> historicalRate * (1.0 - liveWeight) +
                liveFresh.positionsPerHour * liveWeight
        }

        val eta = remaining * 60.0 / effective

        val p80 = input.historical?.absoluteErrorP80Minutes
            ?: (eta * 0.50).coerceAtLeast(30.0)
        val low = (eta - p80).coerceAtLeast(0.0)
        val high = eta + p80

        val confidence = when {
            input.historical != null &&
                input.historical.sampleCount >= 50 &&
                liveFresh != null &&
                liveFresh.sampleCount >= 6 -> ForecastConfidence.HIGH
            input.historical != null &&
                input.historical.sampleCount >= 15 -> ForecastConfidence.MEDIUM
            else -> ForecastConfidence.LOW
        }

        return ForecastResult.Available(
            etaMinutes = eta,
            lowMinutes = low,
            highMinutes = high,
            confidence = confidence,
            effectivePositionsPerHour = effective
        )
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
gradle testForecastDebugUnitTest --tests "com.pylikv.queuewatch.forecast.ForecastEngineV1Test"
```

Expected: PASS.

- [ ] **Step 5: Add edge-case tests from Review Focus**

Add tests for:
- current position larger than queue count does not crash;
- negative/zero rates are ignored;
- live-only forecast is LOW confidence;
- high range is never below ETA and low range is never negative.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/pylikv/queuewatch/forecast app/src/test
git commit -m "feat: add Forecast V1 hybrid ETA engine"
```

---

### Task 4: Add a batch-aware live movement estimator

**Files:**
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/LiveMovementEstimator.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/LiveMovementEstimatorTest.kt`
- Modify: `app/src/main/java/com/pylikv/queuewatch/QueueAnalyzer.kt`

**Interfaces:**
- Consumes: consecutive snapshots for one checkpoint and one vehicle type.
- Produces: `SpeedEstimate?` for the last 60 minutes.
- API:

```kotlin
fun observeSnapshot(
    timestampMillis: Long,
    vehicles: List<QueueVehicle>,
    vehicleType: VehicleType
)

fun estimate(nowMillis: Long): SpeedEstimate?
```

- [ ] **Step 1: Write failing tests**

Required tests:

```kotlin
@Test
fun massRenumberingCountsAsOneBatchMovement() {
    val estimator = LiveMovementEstimator()
    estimator.observePositions(0L, mapOf("A" to 120, "B" to 121, "C" to 122))
    estimator.observePositions(10 * 60_000L, mapOf("A" to 110, "B" to 111, "C" to 112))

    val estimate = estimator.estimate(10 * 60_000L)!!
    assertEquals(60.0, estimate.positionsPerHour, 0.01)
    assertEquals(1, estimate.sampleCount)
}

@Test
fun arrivalsBehindQueueDoNotReduceMeasuredThroughput() {
    val estimator = LiveMovementEstimator()
    estimator.observePositions(0L, mapOf("A" to 20, "B" to 21))
    estimator.observePositions(10 * 60_000L, mapOf("A" to 15, "B" to 16, "NEW" to 40))

    val estimate = estimator.estimate(10 * 60_000L)!!
    assertEquals(30.0, estimate.positionsPerHour, 0.01)
}

@Test
fun backwardsMovementIsNotThroughput() {
    val estimator = LiveMovementEstimator()
    estimator.observePositions(0L, mapOf("A" to 20))
    estimator.observePositions(10 * 60_000L, mapOf("A" to 22))
    assertNull(estimator.estimate(10 * 60_000L))
}
```

- [ ] **Step 2: Implement event aggregation**

Implementation rule:
- compare only vehicle IDs/regnums present in both snapshots;
- compute positive position deltas;
- for each timestamp transition, use the median positive delta as the batch movement;
- the transition contributes exactly one speed sample;
- speed = medianDelta / elapsedHours;
- reject elapsed <= 0 and speed outside 0.25..200 positions/hour;
- retain samples for 60 minutes;
- return median sample speed, not arithmetic mean;
- return `sampleCount` as number of independent snapshot transitions.

- [ ] **Step 3: Run estimator tests**

```bash
gradle testForecastDebugUnitTest --tests "com.pylikv.queuewatch.forecast.LiveMovementEstimatorTest"
```

Expected: PASS.

- [ ] **Step 4: Integrate with QueueAnalyzer without changing vehicle state logic**

In `QueueAnalyzer.processSnapshot`, after parsing the snapshot and before forecast retrieval, feed per-type live positions to one estimator per `VehicleType`. Do not alter `determineState`, `findVehicle`, call processing, or threshold logic.

Expose:

```kotlin
fun getLiveSpeed(vehicleType: VehicleType, nowMillis: Long): SpeedEstimate?
```

- [ ] **Step 5: Add a regression test for vehicle-type separation**

Create a pure estimator test where trucks move and cars do not; querying the car estimator must not reuse truck samples.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/forecast/LiveMovementEstimator.kt \
        app/src/main/java/com/pylikv/queuewatch/QueueAnalyzer.kt \
        app/src/test/java/com/pylikv/queuewatch/forecast/LiveMovementEstimatorTest.kt
git commit -m "feat: estimate live queue movement by batch"
```

---

### Task 5: Load versioned historical baselines

**Files:**
- Create: `app/src/main/assets/forecast_baselines_v1.json`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/HistoricalBaselineRepository.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/HistoricalBaselineRepositoryTest.kt`

**Interfaces:**
- Consumes: compact artifact generated by the QueueLoggerData backtest/baseline plan.
- Produces:

```kotlin
fun find(
    checkpointId: String,
    vehicleType: VehicleType,
    localHour: Int
): HistoricalEstimate?
```

Artifact schema:

```json
{
  "schema_version": 1,
  "algorithm_version": "v1",
  "generated_at": "2026-09-24T00:00:00Z",
  "entries": [
    {
      "checkpoint_id": "98b5be92-d3a5-4ba2-9106-76eb4eb3df49",
      "vehicle_type": "TRUCK",
      "hour_bucket_start": 12,
      "hour_bucket_size": 3,
      "positions_per_hour": 18.4,
      "sample_count": 72,
      "absolute_error_p50_minutes": 24.0,
      "absolute_error_p80_minutes": 52.0
    }
  ]
}
```

- [ ] **Step 1: Write repository tests**

Test exact 3-hour bucket lookup and fallback to an entry with `hour_bucket_start = -1` representing checkpoint+type global fallback.

- [ ] **Step 2: Implement parser and fallback**

Use `org.json.JSONObject` available on Android; keep the repository independent from network access.

Lookup order:
1. checkpoint + type + matching hour bucket
2. checkpoint + type + global bucket (-1)
3. null

- [ ] **Step 3: Run tests**

```bash
gradle testForecastDebugUnitTest --tests "com.pylikv.queuewatch.forecast.HistoricalBaselineRepositoryTest"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/forecast_baselines_v1.json \
        app/src/main/java/com/pylikv/queuewatch/forecast/HistoricalBaselineRepository.kt \
        app/src/test/java/com/pylikv/queuewatch/forecast/HistoricalBaselineRepositoryTest.kt
git commit -m "feat: load versioned historical forecast baselines"
```

---

### Task 6: Integrate Forecast V1 into the foreground service

**Files:**
- Modify: `app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastSessionStore.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/ForecastSessionStoreTest.kt`

**Interfaces:**
- Consumes: confirmed IN_QUEUE vehicle, same-type queue count, live estimate, historical estimate.
- Produces SharedPreferences keys:
  - `forecast_eta_minutes`
  - `forecast_low_minutes`
  - `forecast_high_minutes`
  - `forecast_confidence`
  - `forecast_effective_speed`
  - `forecast_algorithm_version`
  - `forecast_updated_at`
  - `forecast_session_id`
  - `forecast_session_car_key`

- [ ] **Step 1: Implement session identity without exposing regnum externally**

Local session key:

```kotlin
val localCarKey = normalizeRegnum(session.carNumber) + "|" + checkpointId
```

Forecast telemetry session ID:

```kotlin
UUID.randomUUID().toString()
```

Create a new Forecast session ID whenever the local car/checkpoint key changes. The session ID contains no regnum.

- [ ] **Step 2: Add Forecast-only execution gate**

In service code:

```kotlin
val forecastEnabled = resources.getBoolean(R.bool.forecast_enabled)
```

Only calculate/persist Forecast when `forecastEnabled` and the vehicle state is `IN_QUEUE`.

Stable flavor must continue through the original tracking path without invoking Forecast.

- [ ] **Step 3: Calculate and persist Forecast**

On a successful queue snapshot:
1. determine same-type live queue count;
2. obtain live speed from analyzer;
3. obtain historical baseline using checkpoint/type/local hour;
4. call `ForecastEngineV1.estimate`;
5. store result through `ForecastSessionStore`.

If unavailable, clear only Forecast result keys, not tracking keys.

- [ ] **Step 4: Clear/complete Forecast on CALLED**

When confirmed CALLED:
- leave existing alert behavior unchanged;
- mark forecast session completed for telemetry handoff;
- clear visible ETA keys after completion state is persisted.

- [ ] **Step 5: Protect restored sessions**

Add a test for `ForecastSessionStore`: if a saved forecast belongs to a different local car/checkpoint key, read must return null and clear stale forecast keys.

- [ ] **Step 6: Run all unit tests and both builds**

```bash
gradle testForecastDebugUnitTest assembleStableDebug assembleForecastDebug
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt \
        app/src/main/java/com/pylikv/queuewatch/forecast/ForecastSessionStore.kt \
        app/src/test/java/com/pylikv/queuewatch/forecast/ForecastSessionStoreTest.kt
git commit -m "feat: integrate Forecast V1 with tracking service"
```

---

### Task 7: Show Forecast status in the Forecast UI only

**Files:**
- Modify: `app/src/main/java/com/pylikv/queuewatch/MainActivity.kt`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastFormatting.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/ForecastFormattingTest.kt`

**Interfaces:**
- Consumes persisted Forecast result.
- Produces user-visible block:
  - “Ориентировочно …”
  - “Диапазон …”
  - “Надёжность: низкая/средняя/высокая”
  - “Скорость очереди … поз/ч”
  - update timestamp
  - fallback “Недостаточно данных для надёжного прогноза”

- [ ] **Step 1: Write formatter tests**

Example:

```kotlin
@Test
fun formatsHoursAndMinutesWithoutFakePrecision() {
    assertEquals("2 ч 20 мин", formatEtaMinutes(140.0))
}

@Test
fun formatsConfidenceInRussian() {
    assertEquals("средняя", formatConfidence(ForecastConfidence.MEDIUM))
}
```

Round display ETA to 5-minute increments.

- [ ] **Step 2: Implement formatters**

Keep all rounding/display logic outside `MainActivity.kt`.

- [ ] **Step 3: Add a Forecast-only card**

Guard UI with:

```kotlin
val forecastEnabled = context.resources.getBoolean(R.bool.forecast_enabled)
```

Do not hide or replace the existing factual movement card. Add the Forecast card below factual movement.

- [ ] **Step 4: Do not add a forecast alert toggle**

The setup screen must not expose the old dormant forecast-alert settings in V1.

- [ ] **Step 5: Build both variants**

```bash
gradle assembleStableDebug assembleForecastDebug
```

Manual verification:
- stable app has no Forecast card;
- Forecast app has the Forecast card;
- both can be installed simultaneously;
- opening/swiping/back navigation still works;
- existing position threshold and CALLED alert settings remain present.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/MainActivity.kt \
        app/src/main/java/com/pylikv/queuewatch/forecast/ForecastFormatting.kt \
        app/src/test/java/com/pylikv/queuewatch/forecast/ForecastFormattingTest.kt
git commit -m "feat: show Forecast V1 status in experimental app"
```

---

### Task 8: Regression gate before field testing

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `docs/superpowers/forecast-v1-field-test-checklist.md`

**Interfaces:**
- Consumes all prior tasks.
- Produces two installable APK artifacts and a repeatable manual regression checklist.

- [ ] **Step 1: Run unit tests in CI**

Add before APK build:

```yaml
      - name: Run Forecast unit tests
        run: gradle testForecastDebugUnitTest
```

- [ ] **Step 2: Add field-test checklist**

Include exact checks:
- install stable + Forecast side by side;
- start same vehicle in only Forecast;
- kill process and verify session restoration;
- reboot and verify service restoration;
- confirm queue count matches tracked vehicle type;
- confirm factual movement still updates;
- confirm Forecast shows unavailable before enough information;
- confirm ETA/range appear after sufficient data;
- confirm Forecast failure does not alter position alert;
- confirm confirmed call still triggers existing CALLED alert;
- confirm stable app has no Forecast UI.

- [ ] **Step 3: Build full regression set**

```bash
python3 tools/verify_materialized_fixes.py
gradle testForecastDebugUnitTest assembleStableDebug assembleForecastDebug
```

Expected: all commands PASS.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android.yml docs/superpowers/forecast-v1-field-test-checklist.md
git commit -m "test: add Forecast V1 regression gate"
```
