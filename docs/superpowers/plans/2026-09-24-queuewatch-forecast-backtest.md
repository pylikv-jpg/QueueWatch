# QueueLoggerData Forecast Backtest and Baseline Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn QueueLoggerData into a reproducible, leakage-free Forecast V1 evaluation pipeline and generate the compact versioned historical baseline artifact consumed by QueueWatch Forecast.

**Architecture:** Analysis lives in the QueueLoggerData repository because that repository owns the raw JSONL history. A standard-library Python pipeline scans records by their internal timestamps, deduplicates IDs, reconstructs independent queue-movement batches and confirmed calls, then performs chronological backtesting and emits both human-readable metrics and `forecast_baselines_v1.json`. QueueWatch consumes only the generated aggregate artifact, never the raw plate-level history.

**Tech Stack:** Python 3 standard library, JSONL, GitHub Actions (optional after the first validated run), QueueLoggerData + QueueWatch repositories.

**Spec:** `pylikv-jpg/QueueWatch:docs/superpowers/specs/2026-09-24-queuewatch-forecast-design.md`

## Global Constraints

- Use record `timestamp`, not folder/file date, as event time.
- Deduplicate by stable record `id` when available.
- Confirmed outcome is a real CALLED/status 2→3 event; simple disappearance is not equivalent.
- Vehicle types and checkpoints are modeled separately.
- Backtest must use only information available at or before each prediction timestamp.
- Shipping baseline contains aggregates only; no registration number is copied into QueueWatch.
- A mass position renumbering is one queue-movement batch, not hundreds of independent throughput observations.
- Recent incomplete/logging-gap periods must be marked and excluded from false “queue stopped” inference.
- Baseline artifact carries schema version, algorithm version, cutoff timestamp, and source summary.

## Review Focus

- Timestamp/file mismatch: historical uploads may contain older records, so directory date must never define chronology.
- Duplicate upload chunks: repeated record IDs must not multiply calls or movement.
- 1000-row chunk boundaries: a file ending at 1000 rows must not be mistaken for a complete observation window.
- Leakage: future movement/call data must not influence a historical prediction.
- Sparse checkpoint/type buckets: the pipeline must fall back to checkpoint+type global estimates rather than emit unstable micro-buckets.

---

### Task 1: Create an analysis branch and data loader

**Repository:** `pylikv-jpg/QueueLoggerData`

**Files:**
- Create: `analysis/forecast_v1/load_data.py`
- Create: `analysis/forecast_v1/models.py`
- Create: `analysis/forecast_v1/tests/test_load_data.py`

**Interfaces:**
- Produces typed dictionaries/dataclasses for queue samples, vehicle movements, and vehicle events.

- [ ] **Step 1: Create branch**

Create `feature/forecast-v1-analysis` from current `main`.

- [ ] **Step 2: Define minimal record models**

```python
from dataclasses import dataclass
from typing import Optional

@dataclass(frozen=True)
class VehicleEvent:
    id: str
    checkpoint_id: str
    checkpoint_name: str
    registration_number: str
    vehicle_type: str
    timestamp: int
    event_type: str
    previous_position: Optional[int]
    current_position: Optional[int]
    previous_status: Optional[int]
    current_status: Optional[int]
```

Create analogous `VehicleMovement` and `QueueSample`.

- [ ] **Step 3: Write loader tests**

Fixture must prove:
- two identical IDs across two files become one record;
- a record stored under `data/2026-09-24` with a timestamp from 2026-09-04 sorts by the timestamp;
- malformed JSONL lines are counted in a quality report rather than silently changing totals.

- [ ] **Step 4: Implement recursive loader**

Scan:
- `data/**/queue_samples/*.jsonl`
- `data/**/vehicle_movements/*.jsonl`
- `data/**/vehicle_events/*.jsonl`

For each dataset return:
- sorted records;
- duplicate count;
- parse error count;
- min/max record timestamps;
- source file count.

- [ ] **Step 5: Run tests**

```bash
python3 -m unittest discover -s analysis/forecast_v1/tests -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add analysis/forecast_v1
git commit -m "feat: load and deduplicate QueueLogger forecast data"
```

---

### Task 2: Build a data-quality report and observation-gap detector

**Files:**
- Create: `analysis/forecast_v1/quality.py`
- Create: `analysis/forecast_v1/tests/test_quality.py`
- Create/generated: `analysis/forecast_v1/output/data_quality.json`

**Interfaces:**
- Consumes loaded records.
- Produces gap windows and quality summary used by backtest filtering.

- [ ] **Step 1: Write gap detection tests**

For sorted snapshot timestamps:
- 1-minute/normal intervals remain continuous;
- a >20 minute gap splits the observation window;
- a file boundary alone does not split a window if timestamps remain continuous.

- [ ] **Step 2: Implement per checkpoint quality statistics**

For every checkpoint:
- record counts by dataset;
- min/max timestamp;
- exact duplicate count;
- malformed row count;
- unique vehicle count by type;
- confirmed call count;
- movement batch count;
- gap count;
- percent of observed duration inside continuous windows.

- [ ] **Step 3: Flag impossible records**

Count and exclude:
- position <= 0;
- forward jump that implies >200 positions/hour for an independent batch;
- event timestamp outside reasonable dataset min/max envelope;
- call before first observed live event for the same reconstructed session.

Do not delete raw data.

- [ ] **Step 4: Generate quality output**

```bash
python3 -m analysis.forecast_v1.quality --data-root data --output analysis/forecast_v1/output/data_quality.json
```

- [ ] **Step 5: Commit code, not generated volatile report unless deliberately versioned**

```bash
git add analysis/forecast_v1/quality.py analysis/forecast_v1/tests/test_quality.py
git commit -m "feat: detect QueueLogger gaps and data-quality risks"
```

---

### Task 3: Reconstruct independent queue-movement batches

**Files:**
- Create: `analysis/forecast_v1/movement.py`
- Create: `analysis/forecast_v1/tests/test_movement.py`

**Interfaces:**
- Consumes vehicle movements grouped by checkpoint/type/timestamp transition.
- Produces:

```python
@dataclass(frozen=True)
class MovementBatch:
    checkpoint_id: str
    vehicle_type: str
    start_timestamp: int
    end_timestamp: int
    moved_positions: float
    positions_per_hour: float
    supporting_vehicles: int
```

- [ ] **Step 1: Write batch tests**

Cases:
- 100 vehicles all move 120→110 at the same transition: one batch, moved_positions=10, not 1000.
- mixed deltas 9/10/10/11: median movement=10.
- negative/backward moves only: no throughput batch.
- one vehicle jump with no corroboration is accepted only as LOW support metadata, not multiplied.

- [ ] **Step 2: Implement batch construction**

Group by checkpoint + vehicle type + near-identical observation transition. For each group:
- take positive `previous_position-current_position`;
- median delta = queue advance;
- elapsed time is derived from prior observation timing for those vehicles when possible;
- reject zero/negative elapsed;
- derive positions/hour;
- cap validity at 200 positions/hour;
- retain `supporting_vehicles`.

- [ ] **Step 3: Add rolling live estimator helper**

Provide:

```python
def live_speed_at(batches, timestamp_ms, window_minutes=60):
    ...
```

Return median rate + independent batch count using only batches with `end_timestamp <= timestamp_ms`.

- [ ] **Step 4: Run tests and commit**

```bash
python3 -m unittest analysis.forecast_v1.tests.test_movement -v
git add analysis/forecast_v1/movement.py analysis/forecast_v1/tests/test_movement.py
git commit -m "feat: derive batch-aware queue throughput"
```

---

### Task 4: Reconstruct confirmed forecast sessions

**Files:**
- Create: `analysis/forecast_v1/sessions.py`
- Create: `analysis/forecast_v1/tests/test_sessions.py`

**Interfaces:**
- Consumes vehicle events.
- Produces sessions ending only in confirmed CALLED.

```python
@dataclass
class HistoricalSession:
    checkpoint_id: str
    checkpoint_name: str
    vehicle_type: str
    vehicle_key: str
    first_seen_at: int
    called_at: int
    observations: list[tuple[int, int]]
```

`vehicle_key` may use registration number inside the analysis process but must never be exported to the baseline artifact.

- [ ] **Step 1: Write reconstruction tests**

Cases:
- ARRIVAL → MOVE → 2→3 produces one completed session;
- DISAPPEARED without 2→3 does not become a completed outcome;
- a later new ARRIVAL for the same plate after a completed call becomes a new session;
- duplicate event IDs do not duplicate the session.

- [ ] **Step 2: Implement session reconstruction**

Prefer explicit transition `previous_status=2 AND current_status=3`; accept `event_type=CALLED` only when the record semantics confirm a call.

Sort by internal timestamp.

- [ ] **Step 3: Strip identifiers from exported structures**

Add `to_anonymous_case()` returning checkpoint/type/timestamps/positions only.

- [ ] **Step 4: Run tests and commit**

```bash
python3 -m unittest analysis.forecast_v1.tests.test_sessions -v
git add analysis/forecast_v1/sessions.py analysis/forecast_v1/tests/test_sessions.py
git commit -m "feat: reconstruct confirmed queue call sessions"
```

---

### Task 5: Implement the Forecast V1 reference formula for backtesting

**Files:**
- Create: `analysis/forecast_v1/forecast_reference.py`
- Create: `analysis/forecast_v1/tests/test_forecast_reference.py`
- Create: `analysis/forecast_v1/fixtures/forecast_golden_cases.json`

**Interfaces:**
- Mirrors Kotlin `ForecastEngineV1` constants and output semantics.
- Produces golden cases that Kotlin tests can also consume manually or by copied fixture.

- [ ] **Step 1: Create golden cases**

Include:
- history-only;
- one live sample with capped weight;
- mature live sample;
- live-only LOW confidence;
- stale live ignored;
- position 1 gives zero ETA;
- no valid speed gives unavailable.

- [ ] **Step 2: Implement the same constants**

```python
LIVE_MAX_AGE_MS = 60 * 60 * 1000
MAX_LIVE_WEIGHT = 0.55
MIN_RATE = 0.25
MAX_RATE = 200.0
ALGORITHM_VERSION = "v1"
```

Use the same live weights:
- <3 samples: 0.15
- 3–5: 0.30
- 6+: 0.55
- live-only: 1.0

- [ ] **Step 3: Run golden tests**

```bash
python3 -m unittest analysis.forecast_v1.tests.test_forecast_reference -v
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add analysis/forecast_v1/forecast_reference.py \
        analysis/forecast_v1/tests/test_forecast_reference.py \
        analysis/forecast_v1/fixtures/forecast_golden_cases.json
git commit -m "test: define Forecast V1 reference formula"
```

---

### Task 6: Build historical baseline cells without sparse overfitting

**Files:**
- Create: `analysis/forecast_v1/baselines.py`
- Create: `analysis/forecast_v1/tests/test_baselines.py`

**Interfaces:**
- Produces baseline entries for:
  - checkpoint + vehicle type + 3-hour bucket when sample_count >= 15
  - checkpoint + vehicle type global fallback when sample_count >= 10

- [ ] **Step 1: Define throughput sample source**

Historical speed must come from independent movement batches, not queue-size deltas.

- [ ] **Step 2: Define local-time bucket**

Convert event timestamp to `Europe/Minsk` using Python `zoneinfo.ZoneInfo("Europe/Minsk")`.

Bucket starts: 0,3,6,9,12,15,18,21.

- [ ] **Step 3: Use robust median speed**

For each eligible cell:
- `positions_per_hour = median(valid_batch_rates)`;
- `sample_count = number of independent batches`.

Do not use each moved vehicle as one sample.

- [ ] **Step 4: Write sparse fallback tests**

If a 3-hour bucket has 8 samples and checkpoint/type global has 40, bucket lookup must use the global entry.

- [ ] **Step 5: Commit**

```bash
git add analysis/forecast_v1/baselines.py analysis/forecast_v1/tests/test_baselines.py
git commit -m "feat: build robust historical throughput baselines"
```

---

### Task 7: Run chronological backtest and calibrate error ranges

**Files:**
- Create: `analysis/forecast_v1/backtest.py`
- Create: `analysis/forecast_v1/tests/test_backtest.py`
- Generated: `analysis/forecast_v1/output/backtest_v1.json`
- Generated: `analysis/forecast_v1/output/backtest_v1.csv`

**Interfaces:**
- Consumes reconstructed sessions and movement batches.
- Produces prediction-level results with no future leakage.

- [ ] **Step 1: Add explicit leakage test**

Create a fixture where a very fast movement occurs after prediction time. Assert the forecast at the earlier timestamp is unchanged when that future batch is added to the dataset.

- [ ] **Step 2: Select historical prediction observations**

For every completed session, evaluate observations in position bands:
- 1–5
- 6–20
- 21–50
- 51–100
- 101+

Use at most one observation per session per band to avoid overweighting long sessions.

- [ ] **Step 3: Build historical baseline as-of prediction time**

For each prediction timestamp:
- historical batches must have `end_timestamp < prediction_timestamp`;
- live batches must be within prior 60 minutes only;
- future CALLED time is used solely as the outcome.

- [ ] **Step 4: Calculate prediction error**

For each available forecast:

```python
actual_minutes = (called_at - prediction_at) / 60000.0
signed_error = predicted_minutes - actual_minutes
absolute_error = abs(signed_error)
```

- [ ] **Step 5: Aggregate metrics**

Produce:
- count;
- median absolute error;
- MAE;
- median signed error;
- p80 absolute error;
- interval coverage;
- by checkpoint/type;
- by position band;
- by confidence;
- by algorithm version.

- [ ] **Step 6: Calibrate interval width**

For each eligible checkpoint/type/hour cell use historical p80 absolute error when sample count supports it; otherwise inherit checkpoint/type global p80.

Do not tune to achieve perfect coverage.

- [ ] **Step 7: Run backtest**

```bash
python3 -m analysis.forecast_v1.backtest \
  --data-root data \
  --output-json analysis/forecast_v1/output/backtest_v1.json \
  --output-csv analysis/forecast_v1/output/backtest_v1.csv
```

- [ ] **Step 8: Commit code and a compact summary**

Commit the script plus a concise Markdown summary of the run date, data cutoff, sample counts, and metrics; do not commit plate-level CSV if it contains registration numbers.

---

### Task 8: Generate the shipping baseline artifact

**Files:**
- Create: `analysis/forecast_v1/export_baselines.py`
- Generated: `analysis/forecast_v1/output/forecast_baselines_v1.json`
- Copy to QueueWatch branch: `app/src/main/assets/forecast_baselines_v1.json`

**Interfaces:**
- Produces exact schema expected by `HistoricalBaselineRepository`.

- [ ] **Step 1: Generate baseline using all valid data up to a fixed cutoff**

The cutoff must be explicit:

```json
{
  "schema_version": 1,
  "algorithm_version": "v1",
  "generated_at": "...",
  "data_cutoff": "...",
  "source": {
    "repository": "pylikv-jpg/QueueLoggerData",
    "record_timestamp_based": true
  },
  "entries": []
}
```

- [ ] **Step 2: Validate artifact privacy**

Fail export if any key contains:
- `regnum`
- `registration_number`
- `plate`

Fail if any value equals a known registration number from source records.

- [ ] **Step 3: Validate every numeric entry**

Require:
- speed > 0 and <= 200;
- sample_count >= threshold;
- p50/p80 >= 0;
- p80 >= p50;
- valid checkpoint/type;
- hour bucket in {-1,0,3,6,9,12,15,18,21}.

- [ ] **Step 4: Copy artifact to QueueWatch Forecast branch**

Update only `app/src/main/assets/forecast_baselines_v1.json`; record QueueLoggerData source commit SHA in the QueueWatch commit message.

- [ ] **Step 5: Commit both repositories**

QueueLoggerData:

```bash
git add analysis/forecast_v1
git commit -m "feat: export Forecast V1 historical baselines"
```

QueueWatch:

```bash
git add app/src/main/assets/forecast_baselines_v1.json
git commit -m "data: update Forecast V1 baseline from QueueLoggerData <SHA>"
```

---

### Task 9: Define the future correction loop using central telemetry

**Files:**
- Create: `analysis/forecast_v1/model_update_policy.md`

**Interfaces:**
- Consumes Supabase aggregate accuracy metrics plus new QueueLoggerData history.
- Produces a human-reviewed decision process for algorithm `v2`, `v3`, etc.

- [ ] **Step 1: Require minimum evidence before correction**

A parameter cell may be changed only if:
- >= 30 completed telemetry predictions for that checkpoint/type segment, or a broader aggregate is used;
- signed bias is directionally consistent;
- backtest on QueueLoggerData does not regress materially;
- a new `algorithm_version` is assigned.

- [ ] **Step 2: Prohibit silent online self-training**

Document:
- clients never modify shared parameters automatically;
- telemetry is evidence, not direct training feedback;
- changes are generated centrally, reviewed, backtested, versioned, then shipped.

- [ ] **Step 3: Define promotion gate to main QueueWatch**

Before merging Forecast into stable:
- field telemetry sample size documented;
- median absolute error/MAE documented by major checkpoint/type groups;
- interval coverage documented;
- no tracking regression;
- stable and Forecast source are rebased on latest stable QueueWatch;
- forecast code merges forward into current main, never by replacing files with an old Forecast snapshot.

- [ ] **Step 4: Commit**

```bash
git add analysis/forecast_v1/model_update_policy.md
git commit -m "docs: define Forecast model correction and promotion policy"
```
