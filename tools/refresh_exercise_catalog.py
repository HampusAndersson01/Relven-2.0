"""Regenerate the bundled exercise metadata from a pinned public-domain source.

Media paths are intentionally omitted. The app never fetches remote exercise images.
"""

import argparse
import json
import uuid
from pathlib import Path
from urllib.request import urlopen


SOURCE_COMMIT = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
SOURCE_URL = (
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/"
    f"{SOURCE_COMMIT}/dist/exercises.json"
)
OUTPUT = Path("app/assets/relven/exercises-v1.json")


def convert(records):
    result = []
    seen = set()
    for item in records:
        source_id = item.get("id")
        name = item.get("name")
        instructions = item.get("instructions")
        if not source_id or not name or not instructions:
            continue
        if source_id in seen:
            raise ValueError(f"Duplicate exercise source ID: {source_id}")
        seen.add(source_id)
        exercise_id = str(
            uuid.uuid5(
                uuid.NAMESPACE_URL,
                f"https://github.com/yuhonas/free-exercise-db/exercises/{source_id}",
            )
        )
        result.append(
            {
                "id": exercise_id,
                "sourceId": source_id,
                "name": name.strip(),
                "instructions": [step.strip() for step in instructions if step.strip()],
                "category": item.get("category"),
                "equipment": item.get("equipment"),
                "primaryMuscles": item.get("primaryMuscles") or [],
                "secondaryMuscles": item.get("secondaryMuscles") or [],
                "movementType": item.get("mechanic"),
                "difficulty": item.get("level"),
                "source": "yuhonas/free-exercise-db",
                "sourceRevision": SOURCE_COMMIT,
                "revision": 1,
            }
        )
    return sorted(result, key=lambda row: row["sourceId"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, help="Use a local copy of the pinned source")
    args = parser.parse_args()
    if args.input:
        records = json.loads(args.input.read_text(encoding="utf-8"))
    else:
        with urlopen(SOURCE_URL, timeout=30) as response:
            records = json.load(response)
    result = convert(records)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Wrote {len(result)} exercises to {OUTPUT}")


if __name__ == "__main__":
    main()
