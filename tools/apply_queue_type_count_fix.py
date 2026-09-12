from pathlib import Path

path = Path("app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt")
text = path.read_text(encoding="utf-8")

# Already patched: keep this build step idempotent.
if "sameTypeLiveQueueCount" in text:
    print("Queue type count fix already applied")
    raise SystemExit(0)

# Current compact QueueWatchService implementation.
compact_old = '''                        saveQueueCount(vehicles.size)\n                        saveLastUpdate()\n\n                        val vehicle = analyzer.findVehicle(json, session.carNumber)\n'''
compact_new = '''                        saveLastUpdate()\n\n                        val vehicle = analyzer.findVehicle(json, session.carNumber)\n'''

if compact_old in text:
    text = text.replace(compact_old, compact_new, 1)

    compact_anchor = '''                        } else {\n                            vehicleWasConfirmed = true\n\n                            when (analyzer.determineState(vehicle)) {\n'''
    compact_replacement = '''                        } else {\n                            vehicleWasConfirmed = true\n\n                            // Count only live-queue vehicles of the same type\n                            // as the vehicle currently being tracked.\n                            val sameTypeLiveQueueCount = vehicles.count { candidate ->\n                                candidate.vehicleType == vehicle.vehicleType &&\n                                    analyzer.determineState(candidate) == VehicleState.IN_QUEUE\n                            }\n                            saveQueueCount(sameTypeLiveQueueCount)\n\n                            when (analyzer.determineState(vehicle)) {\n'''

    if compact_anchor not in text:
        raise SystemExit("Queue type count fix: compact vehicle anchor not found")

    text = text.replace(compact_anchor, compact_replacement, 1)

else:
    # Legacy formatted QueueWatchService implementation used by older builds.
    old = '''            saveQueueCount(\n                vehicles.size\n            )\n\n\n            saveLastUpdate()\n\n\n            val vehicle =\n                analyzer.findVehicle(\n                    json,\n                    carNumber\n                )\n'''

    new = '''            saveLastUpdate()\n\n\n            val vehicle =\n                analyzer.findVehicle(\n                    json,\n                    carNumber\n                )\n'''

    if old not in text:
        raise SystemExit("Queue type count fix: source anchor not found")

    text = text.replace(old, new, 1)

    anchor = '''            onVehicleConfirmedChange(\n                true\n            )\n\n\n            when (\n'''

    replacement = '''            onVehicleConfirmedChange(\n                true\n            )\n\n\n            /*\n             * Показываем размер очереди только для того же\n             * типа транспорта, что и отслеживаемый автомобиль.\n             * Логика единая для всех пунктов пропуска.\n             * В счётчик входят только автомобили, которые\n             * действительно находятся в живой очереди.\n             */\n            val sameTypeLiveQueueCount =\n                vehicles.count { candidate ->\n                    candidate.vehicleType == vehicle.vehicleType &&\n                        analyzer.determineState(candidate) == VehicleState.IN_QUEUE\n                }\n\n            saveQueueCount(\n                sameTypeLiveQueueCount\n            )\n\n\n            when (\n'''

    if anchor not in text:
        raise SystemExit("Queue type count fix: vehicle anchor not found")

    text = text.replace(anchor, replacement, 1)

if "sameTypeLiveQueueCount" not in text:
    raise SystemExit("Queue type count fix: validation failed")

path.write_text(text, encoding="utf-8")
print("Queue type count fix applied")
