"""Shared identifiers for flood alert BLE beacons (PC + Android)."""

from __future__ import annotations

# 128-bit custom service UUID — must match the Android ScanFilter.
SERVICE_UUID = "7f3a9c21-b5e4-4d8f-a1c6-2e8b4f9d0a73"

# Prefix broadcast in BLE local names for flood beacons.
DEVICE_NAME_PREFIX = "CHEIAS_"

# Affected zones — one beacon per zone (run bunny_hop_beacon.py --zona <id> on each PC).
ZONES: dict[str, dict[str, str]] = {
    "1": {
        "device_name": "CHEIAS_ZONA_1",
        "label": "Zona 1 — Baixo Mondego",
    },
    "2": {
        "device_name": "CHEIAS_ZONA_2",
        "label": "Zona 2 — Ribeira de Coimbra",
    },
    "3": {
        "device_name": "CHEIAS_ZONA_3",
        "label": "Zona 3 — Figueira da Foz",
    },
}

DEFAULT_ZONE_ID = "1"


def get_zone(zone_id: str) -> dict[str, str]:
    zone = ZONES.get(zone_id)
    if zone is None:
        known = ", ".join(sorted(ZONES))
        raise ValueError(f"Zona desconhecida '{zone_id}'. Opções: {known}")
    return zone


def list_zones() -> str:
    lines = ["Zonas configuradas:"]
    for zone_id, zone in sorted(ZONES.items()):
        lines.append(f"  {zone_id}: {zone['label']} ({zone['device_name']})")
    return "\n".join(lines)
