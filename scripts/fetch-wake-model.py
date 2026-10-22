#!/usr/bin/env python3
"""Download the optional offline speech model used for the "Hey Bots" wake word.

    python3 scripts/fetch-wake-model.py

Saves app/src/main/assets/vosk-model-small-en-us-0.15.zip (about 40 MB) after checking its SHA-256.
The file is ignored by git. Without it the app still builds and runs; only the wake word is unavailable.
The model comes from the Vosk project (Apache 2.0): https://alphacephei.com/vosk/models
"""
import hashlib
import sys
import urllib.request
from pathlib import Path

URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/vosk-model-small-en-us-0.15.zip"


def main():
    if DEST.exists() and hashlib.sha256(DEST.read_bytes()).hexdigest() == SHA256:
        print("Model already present and verified:", DEST)
        return
    DEST.parent.mkdir(parents=True, exist_ok=True)
    tmp = DEST.with_suffix(".part")
    print("Downloading", URL)
    digest = hashlib.sha256()
    with urllib.request.urlopen(URL, timeout=60) as response, open(tmp, "wb") as out:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
            out.write(chunk)
    if digest.hexdigest() != SHA256:
        tmp.unlink(missing_ok=True)
        sys.exit("Checksum mismatch; the download was discarded. Do not use an unverified model.")
    tmp.replace(DEST)
    print("Saved and verified:", DEST)


if __name__ == "__main__":
    main()
