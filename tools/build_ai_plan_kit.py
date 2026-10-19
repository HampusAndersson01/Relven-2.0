"""Build the offline AI Plan Kit from the same schemas used by Relven."""

from pathlib import Path
import argparse
import io
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/assets/relven/relven-ai-plan-kit-v1.zip"
FILES = {
    "README.md": ROOT / "docs/ai-plan-kit/README.md",
    "INSTRUCTIONS.md": ROOT / "docs/ai-plan-kit/INSTRUCTIONS.md",
    "capabilities.json": ROOT / "docs/ai-plan-kit/capabilities.json",
    "prompts/create-training-plan.md": ROOT / "docs/ai-plan-kit/create-training-plan.md",
    "prompts/revise-existing-plan.md": ROOT / "docs/ai-plan-kit/revise-existing-plan.md",
    "schemas/plan-v1.schema.json": ROOT / "schemas/plan-v1.schema.json",
    "schemas/workout-v3.schema.json": ROOT / "schemas/workout-v3.schema.json",
    "examples/plan-running.json": ROOT / "schemas/examples/plan-running.json",
}


def build() -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, path in sorted(FILES.items()):
            entry = zipfile.ZipInfo("relven-ai-plan-kit/" + name)
            entry.date_time = (2026, 1, 1, 0, 0, 0)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            content = path.read_bytes().replace(b"\r\n", b"\n")
            archive.writestr(entry, content, compress_type=zipfile.ZIP_DEFLATED)
    return buffer.getvalue()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if bundled ZIP is stale")
    args = parser.parse_args()
    expected = build()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != expected:
            raise SystemExit("AI Plan Kit ZIP is stale; run tools/build_ai_plan_kit.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(expected)
        print(OUTPUT)
