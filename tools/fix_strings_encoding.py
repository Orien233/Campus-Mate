from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path


COMMON_CHINESE = set(
    "\u7684\u4e00\u662f\u5728\u4e0d\u4e86\u6709\u548c\u4eba\u8fd9"
    "\u4e2d\u5927\u4e3a\u4e0a\u4e2a\u56fd\u6211\u4ee5\u8981\u4ed6"
    "\u65f6\u6765\u7528\u4eec\u751f\u5230\u4f5c\u5730\u4e8e\u51fa"
    "\u5c31\u5206\u5bf9\u6210\u4f1a\u53ef\u4e3b\u53d1\u5e74\u52a8"
    "\u540c\u5de5\u4e5f\u80fd\u4e0b\u8fc7\u5b50\u8bf4\u4ea7\u79cd"
)

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


def _to_cp_bytes(s: str) -> bytes | None:
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


def _score_text(s: str) -> int:
    if not s:
        return 0
    penalty = -200 if "\ufffd" in s else 0
    cjk = sum(1 for ch in s if "\u4e00" <= ch <= "\u9fff")
    common = sum(1 for ch in s if ch in COMMON_CHINESE)
    mojibake = sum(1 for ch in s if ch in MOJIBAKE_HINT_CHARS)
    return penalty + common * 3 + cjk - mojibake * 2


def main() -> int:
    strings_xml = Path("app/src/main/res/values/strings.xml")
    raw = strings_xml.read_text(encoding="utf-8")
    if raw and ord(raw[0]) == 0xFEFF:
        raw = raw[1:]

    root = ET.fromstring(raw)

    changed = 0
    for el in root.findall("string"):
        if el.text is None:
            continue
        orig = el.text

        b = _to_cp_bytes(orig)
        if b is None:
            continue

        cand = b.decode("utf-8", "replace")
        if _score_text(cand) > _score_text(orig) + 5 and cand != orig:
            el.text = cand
            changed += 1

    ET.indent(root, space="    ")
    new_xml = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n"
    strings_xml.write_text(new_xml, encoding="utf-8", newline="\n")

    print(f"updated {changed} string values in {strings_xml}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
