#!/usr/bin/env python3
"""캐릭터 벡터 에셋 생성기 (#66, P-3).

왜 생성기인가
-------------
표정 5종 + 프레임 4장이 **같은 몸**을 공유해야 한다. 손으로 아홉 파일을 그리면 눈 위치가 1px씩
어긋나고, 팔레트를 바꿀 때 아홉 곳을 고쳐야 하며, 장비 레이어(#76)가 붙을 기준점이 흔들린다.
몸을 한 번 정의하고 표정만 갈아끼우면 그 셋이 전부 해결된다.

왜 벡터인가 (AI 래스터 대신)
---------------------------
- 크기 무관 선명: 위젯 56dp·홈 히어로 140dp·성장 인라인 56dp를 한 파일로 쓴다
- 용량: 파일당 ~1.5KB (168px ARGB 비트맵 113KB 대비)
- 설정 비의존: `drawable-night` 변형이 필요 없어 위젯 비트맵 캐시(#246) 전제가 유지된다
- 장비 레이어 규약(`character_{state}_{frame}`)에 그대로 얹힌다

실행: `python3 tools/character_assets.py` → kotlin/app/src/main/res/drawable/ 에 덮어쓴다.
"""

from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "kotlin/app/src/main/res/drawable"

# ── 팔레트 ──
# 몸통 앰버는 라이트(#FDF1E4 카드)·다크(#1F1B13 카드) 양쪽에서 읽힌다 — 캐릭터는 테마 변형이 없다.
BODY = "#FFB74D"
BELLY = "#FFD9A6"
INK = "#3E2723"
BLUSH = "#EF7A5C"
WHITE = "#FFFFFF"
SPARK = "#FFCC5C"

STROKE = 2.4


def ell(cx, cy, rx, ry):
    """타원 path — VectorDrawable엔 <ellipse>가 없어 호 두 개로 그린다."""
    return f"M{cx - rx},{cy}a{rx},{ry} 0 1,0 {2 * rx},0a{rx},{ry} 0 1,0 {-2 * rx},0z"


def fill(color, data, alpha=None):
    a = f'\n        android:fillAlpha="{alpha}"' if alpha is not None else ""
    return f'    <path\n        android:fillColor="{color}"{a}\n        android:pathData="{data}" />'


def stroke(color, data, width=STROKE):
    return (
        f'    <path\n        android:pathData="{data}"\n'
        f'        android:strokeColor="{color}"\n'
        f'        android:strokeWidth="{width}"\n'
        f'        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />'
    )


def body_paths(lift=0.0, squash=0.0, arm_y=57.0, foot_dx=0.0, droop_right_ear=False):
    """몸통 한 벌.

    :param lift: 위로 띄우는 양(점프)
    :param squash: 눌림(숨쉬기·뒹굴) — 양수면 납작해지고 그만큼 옆으로 퍼진다
    :param arm_y: 팔 높이 (신남은 위로)
    :param foot_dx: 걷기 프레임의 발 어긋남
    """
    t = -lift
    ry = 29 - squash
    rx = 29 + squash * 0.6
    cy = 51 + t + squash * 0.5
    top = cy - ry
    bot = cy + ry
    ear_y = top + 4

    out = []
    # 꼬리 — 몸 뒤
    out.append(fill(BODY, f"M{48 + rx},{cy + 13}c8,0 10,-8 6,-12c2,6 -2,9 -7,8z"))
    # 귀 (몸보다 먼저 그려 밑동이 몸에 묻힌다)
    out.append(fill(BODY, f"M33,{ear_y + 2}c-4.5,-6.5 -3,-14 3.5,-11.5c2,3.5 -0.5,8.5 -3.5,11.5z"))
    if droop_right_ear:
        out.append(fill(BODY, f"M62,{ear_y}c6,-4 12,-1 12,5c-1,4 -6,5 -9,2c1,-3 0,-5 -3,-7z"))
    else:
        out.append(fill(BODY, f"M63,{ear_y + 2}c4.5,-6.5 3,-14 -3.5,-11.5c-2,3.5 0.5,8.5 3.5,11.5z"))
    # 팔 — 몸 **밖으로** 충분히 빼야 팔로 읽힌다. 실루엣에 묻히면 머리 옆 덩어리(볼살)로 보인다.
    out.append(fill(BODY, ell(48 - rx - 1.5, arm_y + t + 6, 5.5, 8.5)))
    out.append(fill(BODY, ell(48 + rx + 1.5, arm_y + t + 6, 5.5, 8.5)))
    # 발
    out.append(fill(BODY, ell(37 - foot_dx, bot + 0.5, 9, 5.5)))
    out.append(fill(BODY, ell(59 + foot_dx, bot + 0.5, 9, 5.5)))
    # 몸통
    out.append(fill(BODY, ell(48, cy, rx, ry)))
    # 배 — 살짝 아래쪽에
    out.append(fill(BELLY, ell(48, cy + 8, rx - 12, ry - 14)))
    return out, cy


def face_default(cy, look_dx=0.0):
    """기본 눈 — 큰 점눈 + 하이라이트."""
    ey = cy - 5
    return [
        fill(INK, ell(39 + look_dx, ey, 5.5, 6)),
        fill(INK, ell(57 + look_dx, ey, 5.5, 6)),
        fill(WHITE, ell(41 + look_dx, ey - 2.5, 2, 2)),
        fill(WHITE, ell(59 + look_dx, ey - 2.5, 2, 2)),
    ]


def eyes_arc(cy, down=False):
    """감은 눈 / 웃는 눈 — down=True면 뿌듯한 ^^, False면 편안한 ⌣."""
    ey = cy - 5
    if down:
        return [
            stroke(INK, f"M34,{ey + 1}c2.5,-5 7.5,-5 10,0"),
            stroke(INK, f"M52,{ey + 1}c2.5,-5 7.5,-5 10,0"),
        ]
    return [
        stroke(INK, f"M34,{ey - 1}c2.5,4.5 7.5,4.5 10,0"),
        stroke(INK, f"M52,{ey - 1}c2.5,4.5 7.5,4.5 10,0"),
    ]


def blush(cy, alpha="0.45"):
    return [
        fill(BLUSH, ell(28.5, cy + 6, 5, 3.5), alpha),
        fill(BLUSH, ell(67.5, cy + 6, 5, 3.5), alpha),
    ]


def sparkle(cx, cy, r):
    """4각 반짝임 — 오목한 변으로 별처럼."""
    return fill(SPARK, f"M{cx},{cy - r}q{r * 0.28},{r * 0.72} {r},{r}q-{r * 0.72},{r * 0.28} -{r},{r}q-{r * 0.28},-{r * 0.72} -{r},-{r}q{r * 0.72},-{r * 0.28} {r},-{r}z")


def vector(paths):
    body = "\n".join(paths)
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="96dp"\n'
        '    android:height="96dp"\n'
        '    android:viewportWidth="96"\n'
        '    android:viewportHeight="96">\n'
        "    <!-- 생성: tools/character_assets.py (#66) — 손으로 고치지 말고 생성기를 고칠 것 -->\n"
        f"{body}\n"
        "</vector>\n"
    )


def build():
    files = {}

    # ── 기본 프레임 (idle 2 · walk 2) ──
    for name, squash, foot_dx in [("idle_0", 0.0, 0.0), ("idle_1", 1.2, 0.0), ("walk_0", 0.0, 3.0), ("walk_1", 0.6, -3.0)]:
        paths, cy = body_paths(squash=squash, foot_dx=foot_dx)
        paths += blush(cy) + face_default(cy)
        paths.append(stroke(INK, f"M43,{cy + 8}c2.5,3.5 7.5,3.5 10,0"))
        files[f"character_{name}"] = paths

    # ── 표정 5종 (mood_triggers.json의 face 키와 1:1) ──

    # 평온 — 잔잔한 미소
    paths, cy = body_paths()
    paths += blush(cy) + face_default(cy)
    paths.append(stroke(INK, f"M43,{cy + 8}c2.5,3.5 7.5,3.5 10,0"))
    files["character_calm_smile_0"] = paths

    # 신남 — 방방 점프: 몸이 뜨고 팔이 올라가며 입이 벌어진다
    paths, cy = body_paths(lift=4.0, arm_y=46.0)
    paths += blush(cy, "0.6") + eyes_arc(cy, down=True)
    paths.append(fill(INK, ell(48, cy + 9, 7, 5.5)))
    paths.append(fill(BLUSH, ell(48, cy + 11, 3.5, 2.5)))
    paths.append(stroke(BODY, "M14,74c3,3 3,6 1,9", 2.6))
    paths.append(stroke(BODY, "M82,74c-3,3 -3,6 -1,9", 2.6))
    files["character_jump_hyped_0"] = paths

    # 뿌듯 — 가슴 펴고 반짝: 눈을 접고 반짝임을 두른다
    paths, cy = body_paths(squash=-1.5)
    paths += blush(cy) + eyes_arc(cy, down=False)
    paths.append(stroke(INK, f"M43,{cy + 8}c2,3 5,3.5 8,1.5"))
    paths += [sparkle(19, 24, 5.5), sparkle(78, 21, 4.5), sparkle(82, 62, 3.5)]
    files["character_proud_sparkle_0"] = paths

    # 심심 — 두리번·발 까딱: 눈이 옆을 보고 한쪽 귀가 처진다
    paths, cy = body_paths(foot_dx=2.0, droop_right_ear=True)
    paths += blush(cy, "0.3") + face_default(cy, look_dx=3.0)
    paths.append(stroke(INK, f"M44,{cy + 9}c3,-1.5 6,-1.5 9,0"))
    files["character_bored_lookaround_0"] = paths

    # 휴식중 — 이불 속 뒹굴: 몸이 납작해지고 눈을 감고 zzz
    paths, cy = body_paths(squash=6.0, arm_y=60.0)
    paths += blush(cy, "0.35") + eyes_arc(cy, down=False)
    paths.append(stroke(INK, f"M45,{cy + 9}c1.5,2 4.5,2 6,0"))
    paths.append(stroke(INK, "M64,26h8l-8,9h8", 2.2))
    paths.append(stroke(INK, "M76,14h6l-6,7h6", 1.9))
    files["character_cozy_roll_0"] = paths

    for name, paths in files.items():
        (OUT / f"{name}.xml").write_text(vector(paths), encoding="utf-8")
    return sorted(files)


if __name__ == "__main__":
    for name in build():
        print("wrote", name)
