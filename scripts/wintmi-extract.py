#!/usr/bin/env python3
"""
WinTMI → app-seed-import extractor.

KEY DATA SOURCE (verified via WinTMI UI screenshot + Benedetto Balboni record
cross-check on 2026-04-10): the student record in `current.sd7` contains a
denormalized "last assignment date" field for each part type (and for the
"Assistente" marker), stored as a week counter.

STUDENT RECORD LAYOUT (current.sd7, 160-byte records, first record is header):
    0x00  nome (CP1252, 17 bytes, NUL-padded)
    0x11  cognome (CP1252, 17 bytes, NUL-padded)
    0x22  sid (u8)
    0x26  sex_byte (0x80=M, 0x40=F, 0x00=unknown)
    0x28  elig_byte (bitmask: see ELIG_BIT)
    0x38  ultimaParte table — 6 × 8-byte slots:
            slot 0: DISCORSO
            slot 1: LETTURA_DELLA_BIBBIA
            slot 2: INIZIARE_CONVERSAZIONE
            slot 3: COLTIVARE_INTERESSE
            slot 4: FARE_DISCEPOLI
            slot 5: ASSISTENTE (not a real partType, ignored)
        Each slot: counter@+0 (u16 LE), flag@+2 (u8), padding@+3..+7.
        counter == 0  → never assigned
        counter > 0   → last assignment week (ANCHOR_COUNTER = 1218 = 2026-05-04)
        flag semantics not fully understood; observed values: 0, 1, 2. For
        normal emitted entries we accept any flag as long as counter > 0 and
        the student is still eligible for the part type (elig byte check).

- Parttype IDs (kept here for reference, but no longer used for dating):
    19 = LETTURA_DELLA_BIBBIA
    20 = INIZIARE_CONVERSAZIONE (Primo contatto)
    21 = COLTIVARE_INTERESSE (Visita ulteriore)
    24 = FARE_DISCEPOLI (Studio biblico)
    25 = DISCORSO

- Date anchor (verified via PROGRAM.RTF 2026-05-04 … 2026-05-25):
    counter 1218 = Monday 2026-05-04.
    Cutoff: counter ≤ 1214 (Monday 2026-04-06, the last past-or-current Monday
    ≤ TODAY 2026-04-10).

ATTRIBUTION POLICY (user: "no fallback no guessing"):
- Only emit an ultimaParte entry if counter > 0 AND counter ≤ CUTOFF_COUNTER.
- Drop entries where the student is no longer eligible for that part type
  (elig_byte does not contain the bit).
- Ignore slot 5 (ASSISTENTE) — not a real partType.
- Future-scheduled counters (> cutoff) are excluded so the seed only contains
  true historical assignments.
"""
import json
import struct
from datetime import date, timedelta
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BASE = REPO_ROOT / "SeedDataWorkspace" / "Wintmi"
OUT = REPO_ROOT / "SeedDataWorkspace" / "wintmi-seed.json"
SCHEMA = REPO_ROOT / "docs" / "json-schema" / "app-seed-import.schema.json"

# slot index in the record → partType code
SLOT_TO_CODE = {
    0: "DISCORSO",
    1: "LETTURA_DELLA_BIBBIA",
    2: "INIZIARE_CONVERSAZIONE",
    3: "COLTIVARE_INTERESSE",
    4: "FARE_DISCEPOLI",
    # slot 5 = ASSISTENTE marker, intentionally skipped
}

ELIG_BIT = {
    0x02: "DISCORSO",
    0x04: "LETTURA_DELLA_BIBBIA",
    0x08: "INIZIARE_CONVERSAZIONE",
    0x10: "COLTIVARE_INTERESSE",
    0x20: "FARE_DISCEPOLI",
}
PUO_ASSISTERE_BIT = 0x40

# Date anchor (verified)
ANCHOR_COUNTER = 1218
ANCHOR_DATE = date(2026, 5, 4)
TODAY = date(2026, 4, 10)
CUTOFF_COUNTER = 1214  # Monday 2026-04-06, last past-or-current Monday ≤ TODAY

FORCE_F = {"fortuna de santis", "ester sergi"}

# --- readers ---------------------------------------------------------------

def counter_to_date(c: int) -> date:
    return ANCHOR_DATE + timedelta(weeks=c - ANCHOR_COUNTER)

def read_cp1252(buf: bytes, off: int, size: int) -> str:
    raw = buf[off:off + size].split(b"\x00", 1)[0].rstrip()
    try:
        return raw.decode("cp1252").strip()
    except UnicodeDecodeError:
        return ""

def read_ultima_parte(rec: bytes) -> dict[str, int]:
    """Extract {code: counter} from student record, filtered by cutoff."""
    result: dict[str, int] = {}
    for slot, code in SLOT_TO_CODE.items():
        so = 0x38 + slot * 8
        counter = struct.unpack_from("<H", rec, so)[0]
        if counter == 0:
            continue
        if counter > CUTOFF_COUNTER:
            continue
        result[code] = counter
    return result

def load_students():
    with open(BASE / "current.sd7", "rb") as f:
        sd7 = f.read()
    REC_SZ = 160
    students_by_sid = {}
    for i in range((len(sd7) - REC_SZ) // REC_SZ):
        off = REC_SZ + i * REC_SZ
        rec = sd7[off:off + REC_SZ]
        nome = read_cp1252(rec, 0x00, 0x11)
        cognome = read_cp1252(rec, 0x11, 0x11)
        if not nome and not cognome:
            continue
        sid = rec[0x22]
        sex_byte = rec[0x26]
        elig = rec[0x28]
        students_by_sid[sid] = {
            "sid": sid,
            "nome": nome,
            "cognome": cognome,
            "sex_byte": sex_byte,
            "elig_byte": elig,
            "ultima_raw": read_ultima_parte(rec),
        }
    return students_by_sid

def derive_sex(stu: dict) -> str:
    key = f"{stu['nome']} {stu['cognome']}".strip().lower()
    if key in FORCE_F:
        return "F"
    sb = stu["sex_byte"]
    if sb & 0x80:
        return "M"
    if sb & 0x40:
        return "F"
    raise ValueError(f"Cannot derive sex for {stu['nome']} {stu['cognome']} (sex_byte={sb:#04x})")

def canonical_eligibility(stu: dict, sex: str) -> tuple[list[str], bool]:
    elig = stu["elig_byte"]
    codes = [code for bit, code in ELIG_BIT.items() if elig & bit]
    # UOMO safety net: women can never lead LETTURA or DISCORSO
    if sex == "F":
        codes = [c for c in codes if c not in ("LETTURA_DELLA_BIBBIA", "DISCORSO")]
    puo_assistere = bool(elig & PUO_ASSISTERE_BIT)
    return codes, puo_assistere

# --- schema check ----------------------------------------------------------

def validate_schema(obj):
    try:
        import jsonschema  # type: ignore
    except ImportError:
        print("[warn] jsonschema not installed; skipping schema check")
        return
    with open(SCHEMA) as f:
        schema = json.load(f)
    jsonschema.validate(obj, schema)
    print("schema validation: OK")

# --- main ------------------------------------------------------------------

def main():
    students_by_sid = load_students()

    print(f"students: {len(students_by_sid)}")

    part_types = [
        {"code": "LETTURA_DELLA_BIBBIA", "label": "Lettura della Bibbia",
         "peopleCount": 1, "sexRule": "UOMO", "sortOrder": 0},
        {"code": "INIZIARE_CONVERSAZIONE", "label": "Iniziare conversazioni",
         "peopleCount": 2, "sexRule": "STESSO_SESSO", "sortOrder": 1},
        {"code": "COLTIVARE_INTERESSE", "label": "Coltivare l'interesse",
         "peopleCount": 2, "sexRule": "STESSO_SESSO", "sortOrder": 2},
        {"code": "FARE_DISCEPOLI", "label": "Fare discepoli",
         "peopleCount": 2, "sexRule": "STESSO_SESSO", "sortOrder": 3},
        {"code": "DISCORSO", "label": "Discorso",
         "peopleCount": 1, "sexRule": "UOMO", "fixed": True, "sortOrder": 4},
    ]

    ordered = sorted(
        students_by_sid.values(),
        key=lambda s: ((s["cognome"] or "").lower(), (s["nome"] or "").lower()),
    )

    students_json = []
    sex_inferred = []
    total_ultima = 0
    students_with_ultima = 0
    coverage_by_code = {code: 0 for code in SLOT_TO_CODE.values()}
    dropped_for_ineligible = 0
    for stu in ordered:
        try:
            sesso = derive_sex(stu)
        except ValueError as e:
            print(f"  [warn] {e}")
            continue
        if stu["sex_byte"] == 0:
            sex_inferred.append((stu["nome"], stu["cognome"], sesso))
        codes, puo_assistere = canonical_eligibility(stu, sesso)
        stu_latest = stu["ultima_raw"]
        ultima = []
        for code in sorted(stu_latest.keys()):
            counter = stu_latest[code]
            # Only emit if student is still eligible for this code (safety)
            if code not in codes:
                dropped_for_ineligible += 1
                continue
            ultima.append({
                "tipo": code,
                "data": counter_to_date(counter).isoformat(),
            })
            coverage_by_code[code] += 1
        ultima.sort(key=lambda x: x["tipo"])
        entry = {
            "nome": stu["nome"],
            "cognome": stu["cognome"],
            "sesso": sesso,
            "sospeso": False,
            "puoAssistere": puo_assistere,
            "canLeadPartTypeCodes": sorted(codes),
            "ultimaParte": ultima,
        }
        students_json.append(entry)
        if ultima:
            students_with_ultima += 1
            total_ultima += len(ultima)

    doc = {
        "version": 1,
        "partTypes": part_types,
        "students": students_json,
    }

    validate_schema(doc)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print()
    print(f"=== wintmi-seed.json written ===")
    print(f"students emitted: {len(students_json)}")
    print(f"students with ultimaParte: {students_with_ultima}")
    print(f"total ultimaParte entries: {total_ultima}")
    print(f"ultimaParte coverage by code:")
    for code, count in coverage_by_code.items():
        print(f"  {code:<25} {count}")
    print(f"dropped (student no longer eligible for historical type): {dropped_for_ineligible}")
    print(f"sex inferred (sex_byte=0): {sex_inferred}")
    print(f"cutoff counter: {CUTOFF_COUNTER} = {counter_to_date(CUTOFF_COUNTER)}")

if __name__ == "__main__":
    main()
