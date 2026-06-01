from __future__ import annotations

import re
from pathlib import Path


def to_cp_bytes(s: str) -> bytes | None:
    out = bytearray()
    for ch in s:
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


def candidate(s: str) -> str | None:
    b = to_cp_bytes(s)
    if b is None:
        return None
    return b.decode("utf-8", "replace")


def main() -> int:
    p = Path("app/src/main/res/values/strings.xml")
    text = p.read_text(encoding="utf-8")
    if text and ord(text[0]) == 0xFEFF:
        text = text[1:]

    names = [
        "dashboard_weather_update",
        "dashboard_weather_humidity",
        "dashboard_weather_wind",
        "settings_daily_goal_invalid",
        "import_preview_summary",
        "statistics_heatmap_subtitle",
    ]

    for nm in names:
        m = re.search(
            rf'<string\b[^>]*name="{re.escape(nm)}"[^>]*>(.*?)</string>',
            text,
            re.DOTALL,
        )
        if not m:
            print(f"missing {nm}")
            continue
        v = m.group(1)
        c = candidate(v)
        print(f"--- {nm}")
        print("orig:", v.encode("unicode_escape").decode("ascii"))
        if c is None:
            print("cand: <none>")
        else:
            print("cand:", c.encode("unicode_escape").decode("ascii"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
