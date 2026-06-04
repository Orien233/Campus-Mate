from __future__ import annotations

import re
from pathlib import Path


# Characters commonly seen when UTF-8 Chinese text was decoded as GBK/CP936.
# Keep this list ASCII-escaped so the repair tool itself cannot become mojibake.
MOJIBAKE_HINT_CHARS = {
    "\u9359",
    "\u9352",
    "\u93c1",
    "\u6942",
    "\u6d93",
    "\u7f01",
    "\u7f02",
    "\u95bf",
    "\u6553",
}


def to_cp_bytes(s: str) -> bytes | None:
    out = bytearray()
    for ch in s:
        if ch == "\u20ac":
            out.append(0x80)
            continue
        try:
            out.extend(ch.encode("gb18030"))
        except UnicodeEncodeError:
            return None
    return bytes(out)


def looks_mojibake(s: str) -> bool:
    if not s:
        return False
    if "\ufffd" in s or "\u20ac" in s:
        return True
    hits = sum(1 for ch in s if ch in MOJIBAKE_HINT_CHARS)
    return hits >= 2


def postprocess(s: str) -> str:
    # Restore placeholders where '%' became '?' during manual recovery.
    s = re.sub(r"\?(\d+)\$", r"%\1$", s)
    return s.replace("\ufffd", "")


def decode_mojibake(value: str) -> str | None:
    b = to_cp_bytes(value)
    if b is None:
        return None
    return postprocess(b.decode("utf-8", "replace"))


def main() -> int:
    p = Path("app/src/main/res/values/strings.xml")
    text = p.read_text(encoding="utf-8")
    if text and ord(text[0]) == 0xFEFF:
        text = text[1:]

    pat = re.compile(
        r'(<string\b[^>]*name="(?P<name>[^"]+)"[^>]*>)(?P<val>.*?)(</string>)',
        re.DOTALL,
    )

    changed = 0
    out: list[str] = []
    last = 0
    for m in pat.finditer(text):
        out.append(text[last : m.start()])
        head, val, tail = m.group(1), m.group("val"), m.group(4)

        new_val = val
        if looks_mojibake(val):
            cand = decode_mojibake(val)
            if cand is not None and cand != val:
                new_val = cand
                changed += 1

        out.append(head + new_val + tail)
        last = m.end()
    out.append(text[last:])

    p.write_text("".join(out), encoding="utf-8", newline="\n")
    print(f"updated={changed} in {p}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
