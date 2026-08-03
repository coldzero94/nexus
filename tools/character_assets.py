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

# ── 장비 팔레트 (#76) ──
# 몸통 앰버(#FFB74D)와 겹쳐도 읽히도록 채도·명도를 벌린다. 앰버 계열은 장비에 쓰지 않는다.
STRAW = "#E0B268"
STRAW_DK = "#C08F45"
WOOL = "#7E6BC4"
WOOL_DK = "#5F4FA0"
LEAF = "#6FA96B"
LEAF_DK = "#4E8250"
CLOTH = "#E5544B"
CLOTH_DK = "#B93B34"
CORD = "#6B5545"
METAL = "#E8C24A"

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


def hatch():
    """부화 4컷 (#110, E7-6) — 알 → 금 → 갈라짐 → 깨어남.

    캐릭터와 **같은 96×96 좌표계**를 쓴다. 마지막 컷에서 알 껍데기가 벌어지며 그 안에서 몸이
    드러나므로, 알 중심을 캐릭터 몸 중심(48, 51)에 맞춰야 연출이 이어진다.

    프레임이 아니라 **상태 4개**다 — 씬이 진행을 제어하며 하나씩 갈아끼운다. 애니메이션 티커에
    맡기면 리듀스드모션에서 통째로 멈춰 서사가 끊긴다(#228).
    """
    items = {}
    shell = "#F3E3C7"
    shell_dk = "#DCC49B"
    speck = "#C9A87C"

    def egg(crack=None, spots=True):
        out = [
            # 알 — 아래가 둥글고 위가 좁은 달걀꼴
            fill(shell, "M48,16c15,0 26,16 26,31c0,17 -12,29 -26,29c-14,0 -26,-12 -26,-29c0,-15 11,-31 26,-31z"),
            fill(shell_dk, "M48,76c-14,0 -26,-12 -26,-29c0,-3 0.4,-6 1,-9c4,14 13,22 25,22c12,0 21,-8 25,-22c0.6,3 1,6 1,9c0,17 -12,29 -26,29z"),
        ]
        if spots:
            out += [
                fill(speck, ell(38, 40, 4, 3), "0.55"),
                fill(speck, ell(58, 52, 5, 3.5), "0.45"),
                fill(speck, ell(45, 62, 3.5, 2.5), "0.5"),
            ]
        if crack:
            out.append(stroke(INK, crack, 2.2))
        return out

    items["egg_0"] = egg()
    items["egg_1"] = egg(crack="M40,44l6,5l-4,6")
    items["egg_2"] = egg(crack="M34,40l7,6l-5,7l8,5l-4,7l9,4", spots=False)
    # 마지막 컷 — 껍데기가 갈라져 벌어지고 그 사이로 몸이 보인다
    body, cy = body_paths(squash=2.0)
    items["egg_3"] = body + blush(cy) + eyes_arc(cy, down=True) + [
        stroke(INK, f"M43,{cy + 8}c2.5,3.5 7.5,3.5 10,0"),
        fill(shell, "M16,70c8,6 20,9 32,9c12,0 24,-3 32,-9l-6,14c-8,4 -17,6 -26,6c-9,0 -18,-2 -26,-6z"),
        fill(shell_dk, "M16,70c8,6 20,9 32,9c12,0 24,-3 32,-9l-2,5c-8,5 -19,8 -30,8c-11,0 -22,-3 -30,-8z"),
        sparkle(20, 22, 5.0),
        sparkle(78, 26, 4.0),
    ]
    return items


def equipment():
    """장비 레이어 (#76) — 캐릭터와 **같은 96×96 좌표계**에 그린다.

    본체 위에 `matchParentSize`로 겹쳐지므로(EquipmentSection), 좌표가 어긋나면 모자가 공중에 뜬다.
    기준: 머리 돔 꼭대기 y≈22, 중심 x=48, 머리 반폭 ≈29. 목/가슴은 y 52~70.
    HEAD는 y 12~34, ACCESSORY는 y 52~72 안에서 그린다.
    """
    items = {}

    # ── HEAD 4종 ──
    items["hat_straw"] = [
        fill(STRAW, ell(48, 30, 31, 6.5)),                       # 챙
        fill(STRAW_DK, "M17,30c10,4 52,4 62,0c-10,6 -52,6 -62,0z"),
        fill(STRAW, "M31,29c0,-11 6,-17 17,-17c11,0 17,6 17,17z"),  # 크라운
        fill(STRAW_DK, "M31,27c11,3 23,3 34,0l0,2c-11,3 -23,3 -34,0z"),
    ]
    items["hat_beanie"] = [
        fill(WOOL, "M29,29c0,-12 7,-19 19,-19c12,0 19,7 19,19z"),
        fill(WOOL_DK, "M28,28c13,3 27,3 40,0l0,5c-13,3 -27,3 -40,0z"),  # 접힌 단
        fill(WOOL, ell(48, 9, 5, 5)),                            # 방울
    ]
    items["band_leaf"] = [
        fill(LEAF, "M27,30c12,4 30,4 42,0l0,4c-12,4 -30,4 -42,0z"),   # 띠
        fill(LEAF_DK, "M66,29c6,-5 12,-3 12,3c-5,4 -10,3 -12,-3z"),   # 옆 잎
        fill(LEAF, "M64,33c6,2 9,7 6,11c-5,-1 -7,-6 -6,-11z"),
    ]
    items["crown_bud"] = [
        fill(METAL, "M28,31l3,-13l7,7l10,-11l10,11l7,-7l3,13z"),
        fill(METAL, "M28,31c13,3 27,3 40,0l0,3c-13,3 -27,3 -40,0z"),
        fill(CLOTH, ell(48, 21, 2.6, 2.6)),
    ]

    # ── ACCESSORY 4종 ──
    # ACCESSORY는 입(y≈59)을 가리지 않게 **목 아래**(y 64~80)에 앉힌다.
    # 처음엔 y 56에 뒀더니 목도리가 입을 통째로 덮어 표정이 사라졌다.
    items["scarf_red"] = [
        fill(CLOTH, "M30,64c11,7 25,7 36,0c2,4 2,7 0,10c-12,6 -24,6 -36,0c-2,-3 -2,-6 0,-10z"),
        fill(CLOTH_DK, "M60,72c6,2 8,9 5,15c-4,1 -7,-1 -8,-4c2,-4 3,-8 3,-11z"),
    ]
    items["collar_bell"] = [
        fill(CORD, "M32,65c10,6 22,6 32,0c1,2 1,4 0,5c-11,6 -21,6 -32,0c-1,-1 -1,-3 0,-5z"),
        fill(METAL, "M48,71c4,0 6,3 6,6c0,2 -2,3 -6,3c-4,0 -6,-1 -6,-3c0,-3 2,-6 6,-6z"),
        fill(CORD, ell(48, 79, 1.6, 1.6)),
    ]
    items["pendant_leaf"] = [
        fill(CORD, "M34,63c8,6 20,6 28,0c1,1 1,2 0,3c-9,6 -19,6 -28,0c-1,-1 -1,-2 0,-3z"),
        fill(LEAF, "M48,68c6,2 9,8 6,13c-6,-1 -9,-7 -6,-13z"),
    ]
    items["pouch_side"] = [
        fill(CORD, "M28,66c13,5 27,5 40,0l0,3c-13,5 -27,5 -40,0z"),
        fill(STRAW_DK, "M64,68c6,0 9,3 9,7c0,5 -3,7 -9,7c-6,0 -9,-2 -9,-7c0,-4 3,-7 9,-7z"),
        fill(CORD, "M56,71c5,2 11,2 16,0l0,3c-5,2 -11,2 -16,0z"),
    ]
    return items


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

    for state, paths in equipment().items():
        files[f"character_{state}_0"] = paths

    for state, paths in hatch().items():
        files[f"character_{state}_0"] = paths

    for name, paths in files.items():
        (OUT / f"{name}.xml").write_text(vector(paths), encoding="utf-8")
    return sorted(files)


if __name__ == "__main__":
    for name in build():
        print("wrote", name)
