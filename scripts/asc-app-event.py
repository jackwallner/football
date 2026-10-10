#!/usr/bin/env python3
"""Create an App Store in-app event from a JSON spec, upload its media, and
optionally submit it for review.

Usage: asc-app-event.py <event.json> [--submit]
Media paths in the spec are relative to the spec file. Territories default to
every territory the app is available in.
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from pathlib import Path

from asc_lib import ASCClient, bearer_token, load_credentials

V2 = "https://api.appstoreconnect.apple.com/v2"


def available_territories(c: ASCClient, app_id: str) -> list[str]:
    avail = c.get(f"/apps/{app_id}/appAvailabilityV2")["data"]["id"]
    url, out = f"{V2}/appAvailabilities/{avail}/territoryAvailabilities?limit=200&include=territory", []
    while url:
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {c.token}"})
        page = json.load(urllib.request.urlopen(req))
        out += [t["relationships"]["territory"]["data"]["id"] for t in page["data"] if t["attributes"]["available"]]
        url = page["links"].get("next")
    return out


def create_event(c: ASCClient, spec: dict, territories: list[str]) -> str:
    body = {"data": {
        "type": "appEvents",
        "attributes": {
            "referenceName": spec["reference_name"],
            "badge": spec["badge"],
            "purpose": spec["purpose"],
            "priority": spec["priority"],
            "purchaseRequirement": spec["purchase_requirement"],
            "primaryLocale": spec["primary_locale"],
            "territorySchedules": [{
                "territories": territories,
                "publishStart": spec["publish_start"],
                "eventStart": spec["event_start"],
                "eventEnd": spec["event_end"],
            }],
        },
        "relationships": {"app": {"data": {"type": "apps", "id": spec["app_id"]}}},
    }}
    if spec.get("deep_link"):
        body["data"]["attributes"]["deepLink"] = spec["deep_link"]
    return c.post("/appEvents", body)["data"]["id"]


def localize(c: ASCClient, event_id: str, spec: dict) -> str:
    attrs = {
        "name": spec["name"],
        "shortDescription": spec["short_description"],
        "longDescription": spec["long_description"],
    }
    existing = c.get(f"/appEvents/{event_id}/localizations")["data"]
    loc = next((l for l in existing if l["attributes"]["locale"] == spec["primary_locale"]), None)
    if loc:
        c.patch(f"/appEventLocalizations/{loc['id']}", {"data": {"type": "appEventLocalizations", "id": loc["id"], "attributes": attrs}})
        return loc["id"]
    body = {"data": {
        "type": "appEventLocalizations",
        "attributes": {"locale": spec["primary_locale"], **attrs},
        "relationships": {"appEvent": {"data": {"type": "appEvents", "id": event_id}}},
    }}
    return c.post("/appEventLocalizations", body)["data"]["id"]


def upload_image(c: ASCClient, loc_id: str, path: Path, asset_type: str) -> None:
    data = path.read_bytes()
    res = c.post("/appEventScreenshots", {"data": {
        "type": "appEventScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data), "appEventAssetType": asset_type},
        "relationships": {"appEventLocalization": {"data": {"type": "appEventLocalizations", "id": loc_id}}},
    }})["data"]
    for op in res["attributes"]["uploadOperations"]:
        chunk = data[op["offset"]:op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op["requestHeaders"]}
        urllib.request.urlopen(urllib.request.Request(op["url"], data=chunk, method=op["method"], headers=headers))
    c.patch(f"/appEventScreenshots/{res['id']}", {"data": {
        "type": "appEventScreenshots", "id": res["id"], "attributes": {"uploaded": True},
    }})


def submit(c: ASCClient, app_id: str, event_id: str) -> str:
    sub = c.post("/reviewSubmissions", {"data": {
        "type": "reviewSubmissions",
        "attributes": {"platform": "IOS"},
        "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
    }})["data"]["id"]
    c.post("/reviewSubmissionItems", {"data": {
        "type": "reviewSubmissionItems",
        "relationships": {
            "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub}},
            "appEvent": {"data": {"type": "appEvents", "id": event_id}},
        },
    }})
    c.patch(f"/reviewSubmissions/{sub}", {"data": {
        "type": "reviewSubmissions", "id": sub, "attributes": {"submitted": True},
    }})
    return sub


def main() -> None:
    spec_path = Path(sys.argv[1])
    spec = json.loads(spec_path.read_text())
    c = ASCClient(bearer_token(*load_credentials()))
    territories = spec.get("territories") or available_territories(c, spec["app_id"])
    event_id = spec.get("event_id") or create_event(c, spec, territories)
    print(f"event {event_id} ({len(territories)} territories)")
    loc_id = localize(c, event_id, spec)
    upload_image(c, loc_id, spec_path.parent / spec["event_card"], "EVENT_CARD")
    upload_image(c, loc_id, spec_path.parent / spec["details_page"], "EVENT_DETAILS_PAGE")
    print("media uploaded")
    if "--submit" in sys.argv:
        print(f"submitted: reviewSubmission {submit(c, spec['app_id'], event_id)}")


if __name__ == "__main__":
    main()
