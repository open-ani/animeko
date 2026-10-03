#!/usr/bin/env python3
"""Clean a sanitized desktop capture into a test fixture without rewriting its selection inputs."""

import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path


class TraceCleaner:
    INPUTS = ("context", "settings", "preference", "defaultPreference")

    @classmethod
    def clean(cls, raw_bytes):
        raw = json.loads(raw_bytes)
        if raw["formatVersion"] != 1 or not raw["frames"]:
            raise ValueError("Expected a nonempty version 1 desktop capture")
        cls.check_addresses(raw)
        initial = {key: raw["frames"][0][key] for key in cls.INPUTS}
        result = {
            "formatVersion": 1,
            "provenance": {
                "capturedAt": raw["capturedAt"],
                "applicationVersion": raw["applicationVersion"],
                "acquisition": raw["acquisition"],
                "searchCacheCleared": raw.get("searchCacheCleared", False),
                "rawSha256": hashlib.sha256(raw_bytes).hexdigest(),
                "rawBytes": len(raw_bytes),
                "rawFrameCount": len(raw["frames"]),
            },
            "request": raw["request"],
            "preferredSourceId": raw["preferredSourceId"],
            "durationMillis": raw["durationMillis"],
            "sources": raw["sources"],
            "initial": initial,
            "media": [],
            "events": [],
        }
        pool = {}
        previous = None
        for frame in raw["frames"]:
            # Drop observations where only the clock advanced. Keep every input/selection change.
            if previous and all(frame[key] == previous[key] for key in frame if key != "elapsedMillis"):
                continue
            event = {"elapsedMillis": frame["elapsedMillis"], "sources": [], "selectedMediaId": frame["selectedMediaId"]}
            for index, source in enumerate(frame["sources"]):
                if previous and source == previous["sources"][index]:
                    continue
                references = []
                for media in source["results"]:
                    # Key by complete value: different revisions of the same media ID must survive.
                    key = json.dumps(media, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
                    if key not in pool:
                        pool[key] = len(result["media"])
                        result["media"].append(media)
                    references.append(pool[key])
                event["sources"].append({
                    "source": index, "state": source["state"], "generation": source["generation"], "media": references,
                })
            for key in cls.INPUTS:
                if previous and frame[key] != previous[key]:
                    event[key] = frame[key]
            result["events"].append(event)
            previous = frame
        cls.verify(raw, result)
        return result

    @classmethod
    def verify(cls, raw, cleaned):
        inputs = dict(cleaned["initial"])
        sources = [None] * len(cleaned["sources"])
        restored = []
        for event in cleaned["events"]:
            for key in cls.INPUTS:
                if key in event:
                    inputs[key] = event[key]
            for change in event["sources"]:
                index = change["source"]
                sources[index] = {
                    "id": cleaned["sources"][index]["id"], "state": change["state"],
                    "generation": change["generation"], "results": [cleaned["media"][i] for i in change["media"]],
                }
            restored.append({
                "elapsedMillis": event["elapsedMillis"], "sources": copy.deepcopy(sources),
                **inputs, "selectedMediaId": event["selectedMediaId"],
            })
        expected = []
        for frame in raw["frames"]:
            if not expected or any(frame[key] != expected[-1][key] for key in frame if key != "elapsedMillis"):
                expected.append(frame)
        if restored != expected:
            raise ValueError("Cleaning changed a recorded source, candidate, timestamp, preference or context")

    @classmethod
    def check_addresses(cls, value):
        if isinstance(value, dict):
            for item in value.values():
                cls.check_addresses(item)
        elif isinstance(value, list):
            for item in value:
                cls.check_addresses(item)
        elif isinstance(value, str):
            scrubbed = value.replace("https://fixture.invalid/", "")
            if any(marker in scrubbed for marker in ("https://", "http://", "magnet:", "file://", "/Users/", "/home/")):
                raise ValueError("Capture contains an unredacted address; sanitize before cleaning")

    @classmethod
    def main(cls):
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument("capture", type=Path)
        parser.add_argument("output", type=Path)
        args = parser.parse_args()
        raw = args.capture.read_bytes()
        if args.capture.suffix == ".gz":
            raw = gzip.decompress(raw)
        fixture = cls.clean(raw)
        encoded = json.dumps(fixture, ensure_ascii=False, indent=2) + "\n"
        args.output.write_text(encoded)
        print(f"{len(raw)} -> {len(encoded.encode())} bytes; {len(fixture['media'])} media values, {len(fixture['events'])} events")


if __name__ == "__main__":
    TraceCleaner.main()
