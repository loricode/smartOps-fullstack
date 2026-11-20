#!/usr/bin/env python3
"""
Validador previo (pre-flight) de archivos para SmartOps.

Revisa estructura y calidad de datos ANTES de enviar el archivo al banco, sin
reglas que dependan del estado de las cuentas (eso lo hace el backend):
cabecera, número de columnas, tipos, fechas, montos, referencias repetidas.

Salida: reporte JSON por stdout. Código de salida 0 si la tasa de errores no
supera --max-error-rate, 1 en caso contrario, 2 si el archivo es ilegible.

Ejemplo:
    python validate_file.py out/operations_GEN123.csv --kind operations --max-error-rate 0.05
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path

SCHEMAS = {
    "operations": ["reference", "type", "source_account", "target_account", "amount", "currency",
                   "value_date", "description"],
    "statement": ["reference", "amount", "currency", "value_date"],
}
REFERENCE_RE = re.compile(r"^[A-Za-z0-9_-]{4,64}$")
ACCOUNT_RE = re.compile(r"^[A-Z0-9]{4,20}$")
TYPES = {"TRANSFER": (True, True), "PAYMENT": (True, False), "COLLECTION": (False, True)}
CURRENCIES = {"COP", "USD"}


def check_amount(value: str) -> str | None:
    try:
        amount = Decimal(value)
    except InvalidOperation:
        return "INVALID_AMOUNT"
    if amount <= 0 or -amount.as_tuple().exponent > 2:
        return "INVALID_AMOUNT"
    return None


def check_date(value: str) -> str | None:
    if not value:
        return "MISSING_FIELD"
    try:
        date.fromisoformat(value)
    except ValueError:
        return "INVALID_DATE"
    return None


def validate_operation(row: dict[str, str]) -> str | None:
    if not REFERENCE_RE.match(row["reference"]):
        return "MALFORMED_RECORD"
    op_type = row["type"].strip().upper()
    if op_type not in TYPES:
        return "INVALID_TYPE"
    if error := check_amount(row["amount"]):
        return error
    if row["currency"].strip().upper() not in CURRENCIES:
        return "INVALID_CURRENCY"
    if error := check_date(row["value_date"]):
        return error
    needs_source, needs_target = TYPES[op_type]
    source, target = row["source_account"].strip(), row["target_account"].strip()
    if bool(source) != needs_source or bool(target) != needs_target:
        return "INVALID_ACCOUNT_COMBINATION"
    if source and target and source == target:
        return "SAME_ACCOUNT"
    if any(acc and not ACCOUNT_RE.match(acc) for acc in (source, target)):
        return "MALFORMED_RECORD"
    return None


def validate_statement(row: dict[str, str]) -> str | None:
    if not row["reference"] or len(row["reference"]) > 64:
        return "MALFORMED_RECORD"
    if error := check_amount(row["amount"]):
        return error
    if len(row["currency"].strip()) != 3:
        return "INVALID_CURRENCY"
    return check_date(row["value_date"])


def validate(path: Path, kind: str, sample_limit: int = 20) -> dict:
    expected = SCHEMAS[kind]
    errors: Counter[str] = Counter()
    samples: list[dict] = []
    seen: set[str] = set()
    total = 0
    with path.open(newline="", encoding="utf-8-sig") as fh:
        reader = csv.reader(fh)
        header = [h.strip().lower() for h in next(reader, [])]
        if header != expected:
            return {"file": str(path), "kind": kind, "valid_header": False,
                    "expected_header": expected, "found_header": header}
        for line_number, fields in enumerate(reader, start=2):
            if not fields or all(not f.strip() for f in fields):
                continue
            total += 1
            min_cols = len(expected) - 1 if kind == "operations" else len(expected)
            if not (min_cols <= len(fields) <= len(expected)):
                code = "MALFORMED_RECORD"
            else:
                row = dict(zip(expected, (f.strip() for f in fields)))
                row.setdefault("description", "")
                code = validate_operation(row) if kind == "operations" else validate_statement(row)
                if code is None:
                    if row["reference"] in seen:
                        code = "DUPLICATE_REFERENCE"
                    seen.add(row["reference"])
            if code:
                errors[code] += 1
                if len(samples) < sample_limit:
                    samples.append({"line": line_number, "code": code, "raw": ",".join(fields)[:200]})

    invalid = sum(errors.values())
    return {
        "file": str(path),
        "kind": kind,
        "valid_header": True,
        "total_records": total,
        "valid_records": total - invalid,
        "invalid_records": invalid,
        "error_rate": round(invalid / total, 6) if total else 0.0,
        "errors_by_code": dict(errors.most_common()),
        "samples": samples,
    }


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Validación previa de archivos SmartOps")
    p.add_argument("file", type=Path)
    p.add_argument("--kind", choices=SCHEMAS, default="operations")
    p.add_argument("--max-error-rate", type=float, default=0.05)
    a = p.parse_args(sys.argv[1:] if argv is None else argv)
    try:
        report = validate(a.file, a.kind)
    except (OSError, UnicodeDecodeError) as e:
        print(json.dumps({"file": str(a.file), "error": str(e)}), file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, ensure_ascii=False))
    if not report["valid_header"]:
        return 1
    return 0 if report["error_rate"] <= a.max_error_rate else 1


if __name__ == "__main__":
    sys.exit(main())
