from pathlib import Path

path = Path("app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt")
text = path.read_text(encoding="utf-8")

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

if "saveQueueCount(\n                vehicles.size" in text:
    raise SystemExit("Queue type count fix: old total counter is still present")

if "sameTypeLiveQueueCount" not in text:
    raise SystemExit("Queue type count fix: validation failed")

path.write_text(text, encoding="utf-8")
print("Queue type count fix applied")
