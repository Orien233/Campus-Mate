from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path


COMMON = set(
    "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通并提直题党程展五果料象员革位入常文总次品式活设及管特件长求老头基资边流路级少图山统接知较将组见计别她手角期根论运农指几九区强放决西被干做必战先回则任取据处队南给色光门即保治北造百规热领七海口东导器压志世金增争济阶油思术极交受联什认六共权收证改清己美再采转更单风切打白教速花带安场身车例真务每"
)

# Characters that are very common in "UTF-8 bytes decoded as GBK/CP936" mojibake.
MOJI_HINT = set("鍙鍒鏁楂樻棩瀛璇绋瀵寮缁熻鎻闂锛锟")


def _to_cp_bytes(s: str) -> bytes | None:
    out = bytearray()
    for ch in s:
        # In CP936, byte 0x80 is often displayed as the Euro sign.
        # Python's gbk encoder doesn't support "€", so handle it explicitly.
        if ch == "€":
            out.append(0x80)
            continue

        for enc in ("gbk", "gb18030"):
            try:
                out.extend(ch.encode(enc))
                break
            except UnicodeEncodeError:
                continue
        else:
            return None
    return bytes(out)


def _score_text(s: str) -> int:
    if not s:
        return 0

    # Replacement char indicates broken UTF-8; penalize, but not too much.
    # Some existing mojibake strings are even worse; we still want to move toward readable Chinese.
    penalty = -200 if "\ufffd" in s else 0
    cjk = sum(1 for ch in s if "\u4e00" <= ch <= "\u9fff")
    common = sum(1 for ch in s if ch in COMMON)
    moji = sum(1 for ch in s if ch in MOJI_HINT)

    # Reward common Chinese, penalize mojibake-ish characters.
    return penalty + common * 3 + cjk - moji * 2


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

        # Replace only if the candidate looks substantially more like natural Chinese.
        if _score_text(cand) > _score_text(orig) + 5 and cand != orig:
            el.text = cand
            changed += 1

    # Write back. Note: ElementTree doesn't preserve formatting; this is acceptable for resources XML.
    new_xml = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode")
    strings_xml.write_text(new_xml, encoding="utf-8", newline="\n")

    print(f"updated {changed} <string> values in {strings_xml}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
