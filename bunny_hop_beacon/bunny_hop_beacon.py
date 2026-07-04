#!/usr/bin/env python3
"""
Bunny Hop BLE Beacon — Linux peripheral advertising via BlueZ.

Announces a flood-alert beacon for one affected zone so the Android
"Bunny Hop" app can scan and notify users when they enter range.

Prerequisites (Debian/Ubuntu):
  sudo apt install bluez python3-gi python3-dbus libglib2.0-dev
  pip install -r requirements.txt

Run (needs CAP_NET_ADMIN or root for LE advertising on most adapters):
  sudo python3 bunny_hop_beacon.py --zona 1
  sudo python3 bunny_hop_beacon.py --list-zonas
  # or, once: sudo setcap cap_net_admin+eip $(readlink -f $(which python3))

BlueZ 5.43+ exposes LE advertising via D-Bus without the old experimental
flag; if registration fails, ensure bluetoothd is running:
  sudo systemctl status bluetooth
"""

from __future__ import annotations

import argparse
import signal
import sys
import time

from gi.repository import GLib

from bunny_hop_constants import DEFAULT_ZONE_ID, SERVICE_UUID, get_zone, list_zones


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Beacon BLE de alerta de cheias para uma zona afetada.",
    )
    parser.add_argument(
        "--zona",
        default=DEFAULT_ZONE_ID,
        help=f"ID da zona a anunciar (predefinido: {DEFAULT_ZONE_ID})",
    )
    parser.add_argument(
        "--list-zonas",
        action="store_true",
        help="Lista zonas disponíveis e termina.",
    )
    return parser.parse_args()


def pick_adapter():
    try:
        from bluezero import adapter
    except ImportError as exc:
        print(
            "[Bunny Hop] Dependências ausentes. Instale com:\n"
            "  pip install -r requirements.txt\n"
            "  sudo apt install python3-gi python3-dbus bluez",
            file=sys.stderr,
        )
        raise SystemExit(1) from exc

    adapters = adapter.list_adapters()
    if not adapters:
        print("[Bunny Hop] Nenhum adaptador Bluetooth encontrado.", file=sys.stderr)
        raise SystemExit(1)

    dongle = adapter.Adapter(adapters[0])
    if not dongle.powered:
        print("[Bunny Hop] Ligando adaptador Bluetooth…")
        dongle.powered = True
        time.sleep(0.5)

    return dongle


def main() -> None:
    args = parse_args()
    if args.list_zonas:
        print(list_zones())
        return

    try:
        zone = get_zone(args.zona)
    except ValueError as err:
        print(f"[Bunny Hop] {err}", file=sys.stderr)
        raise SystemExit(2) from err

    device_name = zone["device_name"]
    zone_label = zone["label"]

    try:
        from bluezero import advertisement
    except ImportError as exc:
        print(
            "[Bunny Hop] Dependências ausentes. Instale com:\n"
            "  pip install -r requirements.txt\n"
            "  sudo apt install python3-gi python3-dbus bluez",
            file=sys.stderr,
        )
        raise SystemExit(1) from exc

    dongle = pick_adapter()
    dongle.alias = device_name

    print(f"[Bunny Hop] Adaptador: {dongle.address}")
    print(f"[Bunny Hop] Zona: {zone_label}")
    print(f"[Bunny Hop] Nome BLE: {device_name}")
    print(f"[Bunny Hop] Service UUID: {SERVICE_UUID}")

    advert = advertisement.Advertisement(1, "peripheral")
    advert.local_name = device_name
    advert.service_UUIDs = [SERVICE_UUID]

    ad_manager = advertisement.AdvertisingManager(dongle.address)
    mainloop = GLib.MainLoop()

    def shutdown(_signum=None, _frame=None) -> None:
        print("\n[Bunny Hop] Parando advertising…")
        try:
            ad_manager.unregister_advertisement(advert)
        except Exception as err:  # noqa: BLE001 — best-effort cleanup
            print(f"[Bunny Hop] Aviso ao desregistrar: {err}", file=sys.stderr)
        mainloop.quit()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    ad_manager.register_advertisement(advert, {})
    print(f"[Bunny Hop] Alerta activo — a anunciar cheias em: {zone_label}")
    print("[Bunny Hop] Ctrl+C para parar.")

    try:
        mainloop.run()
    finally:
        print("[Bunny Hop] Encerrado.")


if __name__ == "__main__":
    main()
