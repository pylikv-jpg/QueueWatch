from pathlib import Path

gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android.yml").read_text(encoding="utf-8")

checks = {
    "flavor dimension": 'flavorDimensions += "mode"' in gradle,
    "stable application id": 'applicationId = "com.pylikv.queuewatch"' in gradle and 'create("stable")' in gradle,
    "forecast application id": 'applicationId = "com.pylikv.queuewatch.forecast"' in gradle,
    "forecast version suffix": 'versionNameSuffix = "-forecast"' in gradle,
    "manifest label resource": 'android:label="@string/app_name"' in manifest,
    "stable app name": Path("app/src/stable/res/values/strings.xml").exists(),
    "forecast app name": Path("app/src/forecast/res/values/strings.xml").exists(),
    "stable forecast flag": Path("app/src/stable/res/values/bools.xml").exists(),
    "forecast forecast flag": Path("app/src/forecast/res/values/bools.xml").exists(),
    "stable debug build": "assembleStableDebug" in workflow,
    "forecast debug build": "assembleForecastDebug" in workflow,
    "stable release bundle": "bundleStableRelease" in workflow,
}

missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit("Missing Forecast flavor configuration: " + ", ".join(missing))

stable_strings = Path("app/src/stable/res/values/strings.xml").read_text(encoding="utf-8")
forecast_strings = Path("app/src/forecast/res/values/strings.xml").read_text(encoding="utf-8")
stable_bools = Path("app/src/stable/res/values/bools.xml").read_text(encoding="utf-8")
forecast_bools = Path("app/src/forecast/res/values/bools.xml").read_text(encoding="utf-8")

if ">QueueWatch<" not in stable_strings:
    raise SystemExit("Stable app_name is not QueueWatch")
if ">QueueWatch Forecast<" not in forecast_strings:
    raise SystemExit("Forecast app_name is not QueueWatch Forecast")
if '<bool name="forecast_enabled">false</bool>' not in stable_bools:
    raise SystemExit("Stable forecast_enabled must be false")
if '<bool name="forecast_enabled">true</bool>' not in forecast_bools:
    raise SystemExit("Forecast forecast_enabled must be true")

print("Forecast flavor configuration verified")
