from __future__ import annotations

import re
from pathlib import Path


# Typical characters that appear frequently in "UTF-8 bytes decoded as GBK/CP936" Chinese mojibake.
MOJIBAKE_HINT_CHARS = set(
    "鍙鍒鏁楂樻棩瀛璇绋瀵寮缁熻鎻闂锛锟鏇宸鏈€浠诲姟璁剧疆"
)


def to_cp_bytes(s: str) -> bytes | None:
    """
    Convert the mojibake string back to the original CP936 byte sequence.
    We primarily use gb18030 since it covers the private-use mappings sometimes seen in bad decodes.
    """
    out = bytearray()
    for ch in s:
        # CP936 byte 0x80 often appears as the Euro sign.
        if ch == "€":
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
    if "�" in s or "€" in s:
        return True
    if "锛" in s:
        return True
    hits = sum(1 for ch in s if ch in MOJIBAKE_HINT_CHARS)
    return hits >= 2


def postprocess(s: str) -> str:
    # Restore placeholders where '%' became '?'.
    s = re.sub(r"\\?(\\d+)\\$", r"%\\1$", s)
    # Replace commonly broken punctuation.
    s = s.replace(" · ", " · ")
    # Drop any remaining replacement chars (best-effort; some strings may still need manual fix).
    s = s.replace("�", "")
    return s


def decode_mojibake(value: str) -> str | None:
    b = to_cp_bytes(value)
    if b is None:
        return None
    decoded = b.decode("utf-8", "replace")
    decoded = postprocess(decoded)
    return decoded


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
    matched = 0
    out: list[str] = []
    last = 0
    for m in pat.finditer(text):
        matched += 1
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
    print(f"matched={matched} updated={changed} in {p}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
