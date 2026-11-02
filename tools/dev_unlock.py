#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Alpha Blocker developer unlock codes.

Someone lost their paper recovery code? Their phone shows a REQUEST code
(Forgot PIN / recovery dialog > "Lost the code? Ask the developer").
This script turns that request code into an UNLOCK code that works only on
that phone, only once. See app/src/main/java/com/alphablocker/security/DevUnlock.kt.

Setup (once):
    pip install cryptography
    python dev_unlock.py keygen
        -> writes alphablocker_dev_private.pem   (SECRET: back it up offline,
           never share or commit it; whoever has it can unlock any phone
           running your build)
        -> prints the PUBLIC_KEY line to paste into security/DevKey.kt

Making a code:
    python dev_unlock.py sign ABCD-EFGH-IJKL-MNOP
    python dev_unlock.py sign ABCD-EFGH-IJKL-MNOP --app io.github.you.alphablocker
        (--app only if you changed applicationId; default com.alphablocker)

Before sending a code, make sure the request really comes from the phone's
owner, and consider asking them to wait a day: the code removes protection.
"""
import argparse
import base64
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
except ImportError:
    sys.exit("Missing library. Run:  pip install cryptography")

CONTEXT = "alphablocker-dev-unlock-v1"
ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
DEFAULT_KEY = "alphablocker_dev_private.pem"


def normalize(code: str) -> str:
    return "".join(c for c in code.upper() if c in ALPHABET)


def keygen(path: Path) -> None:
    if path.exists():
        sys.exit(f"{path} already exists. Keep using it (a new key would invalidate the one in your app).")
    key = ec.generate_private_key(ec.SECP256R1())
    path.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ))
    public = key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    print(f"Private key written to: {path.resolve()}")
    print("KEEP IT SECRET. Back it up offline (e.g. a USB stick in a drawer).")
    print()
    print("Paste this into app/src/main/java/com/alphablocker/security/DevKey.kt, then rebuild:")
    print(f'    const val PUBLIC_KEY = "{base64.b64encode(public).decode()}"')


def sign(request: str, app_id: str, path: Path) -> None:
    req = normalize(request)
    if len(req) != 16:
        sys.exit("The request code should have 16 letters/digits (like ABCD-EFGH-IJKL-MNOP).")
    if not path.exists():
        sys.exit(f"Private key not found: {path}  (run keygen first, or pass --key)")
    key = serialization.load_pem_private_key(path.read_bytes(), password=None)
    message = f"{CONTEXT}:{app_id}:{req}".encode()
    r, s = decode_dss_signature(key.sign(message, ec.ECDSA(hashes.SHA256())))
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    code = base64.b32encode(raw).decode().rstrip("=")
    grouped = "-".join(code[i:i + 5] for i in range(0, len(code), 5))
    print("Unlock code (works once, only on the phone that showed this request):")
    print(grouped)


def main() -> None:
    p = argparse.ArgumentParser(description="Alpha Blocker developer unlock codes")
    sub = p.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("keygen", help="create your private key (once)")
    k.add_argument("--key", default=DEFAULT_KEY)
    s = sub.add_parser("sign", help="make an unlock code from a request code")
    s.add_argument("request")
    s.add_argument("--app", default="com.alphablocker", help="applicationId of your build")
    s.add_argument("--key", default=DEFAULT_KEY)
    a = p.parse_args()
    if a.cmd == "keygen":
        keygen(Path(a.key))
    else:
        sign(a.request, a.app, Path(a.key))


if __name__ == "__main__":
    main()
