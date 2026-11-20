#!/usr/bin/env python3
"""
Generador de archivos de prueba para SmartOps.

Produce, para una misma corrida:
  * operations_<prefix>.csv  -> archivo de operaciones para la carga masiva (con errores inyectados)
  * statement_<prefix>.csv   -> extracto externo para conciliar (con diferencias inyectadas)
  * manifest_<prefix>.json   -> conteos esperados, para verificar el resultado del lote

Solo usa la librería estándar. Determinista con --seed.

Ejemplo:
    python generate_operations.py --count 10000 --error-rate 0.03 --out ./out
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import sys
from dataclasses import dataclass, field
from datetime import date, datetime
from pathlib import Path

OPERATIONS_HEADER = ["reference", "type", "source_account", "target_account", "amount", "currency",
                     "value_date", "description"]
STATEMENT_HEADER = ["reference", "amount", "currency", "value_date"]

# Debe coincidir con database/seed/R__seed_accounts.sql
ACTIVE_ACCOUNTS = [f"ACC{n:07d}" for n in range(1, 200)]
BLOCKED_ACCOUNT = "ACC0000200"
TYPES = ["TRANSFER", "PAYMENT", "COLLECTION"]
TYPE_WEIGHTS = [0.6, 0.25, 0.15]

ERROR_KINDS = {
    # código esperado en SmartOps -> función que corrompe una fila válida
    "INVALID_AMOUNT": lambda row, rnd: row.update(amount=rnd.choice(["-100.00", "0", "12,5", "abc", "10.999"])),
    "ACCOUNT_NOT_FOUND": lambda row, rnd: row.update(**_unknown_account(row)),
    "ACCOUNT_BLOCKED": lambda row, rnd: row.update(**_blocked_account(row)),
    "INVALID_DATE": lambda row, rnd: row.update(value_date=rnd.choice(["2026-13-01", "19/11/2026", ""])),
    "INVALID_TYPE": lambda row, rnd: row.update(type=rnd.choice(["WIRE", "REFUND", ""])),
    "INVALID_CURRENCY": lambda row, rnd: row.update(currency=rnd.choice(["EUR", "XXX"])),
}


def _unknown_account(row: dict) -> dict:
    key = "source_account" if row["source_account"] else "target_account"
    return {key: "ACC9999999"}


def _blocked_account(row: dict) -> dict:
    key = "source_account" if row["source_account"] else "target_account"
    return {key: BLOCKED_ACCOUNT}


@dataclass
class Manifest:
    prefix: str
    value_date: str
    total_lines: int = 0
    expected_valid: int = 0
    expected_rejected: dict[str, int] = field(default_factory=dict)
    statement_lines: int = 0
    expected_matched: int = 0
    expected_amount_mismatch: int = 0
    expected_missing_external: int = 0
    expected_missing_internal: int = 0

    def reject(self, code: str) -> None:
        self.expected_rejected[code] = self.expected_rejected.get(code, 0) + 1


def valid_row(rnd: random.Random, reference: str, value_date: str) -> dict:
    op_type = rnd.choices(TYPES, TYPE_WEIGHTS)[0]
    source, target = rnd.sample(ACTIVE_ACCOUNTS, 2)
    # Montos acotados: con 50M de saldo inicial no se producen rechazos por fondos.
    amount = f"{rnd.randint(1_000, 200_000)}.{rnd.randint(0, 99):02d}"
    return {
        "reference": reference,
        "type": op_type,
        "source_account": source if op_type in ("TRANSFER", "PAYMENT") else "",
        "target_account": target if op_type in ("TRANSFER", "COLLECTION") else "",
        "amount": amount,
        "currency": "COP",
        "value_date": value_date,
        "description": rnd.choice(["Pago proveedor", "Nómina", "Recaudo PSE", "Factura, servicios", ""]),
    }


def generate(count: int, error_rate: float, duplicate_rate: float, malformed_rate: float,
             missing_rate: float, mismatch_rate: float, extra_external: int,
             prefix: str, value_date: str, seed: int, out: Path) -> Manifest:
    rnd = random.Random(seed)
    out.mkdir(parents=True, exist_ok=True)
    manifest = Manifest(prefix=prefix, value_date=value_date)
    valid_for_statement: list[dict] = []
    valid_refs: list[str] = []

    ops_path = out / f"operations_{prefix}.csv"
    with ops_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh, lineterminator="\n")
        writer.writerow(OPERATIONS_HEADER)
        for i in range(1, count + 1):
            reference = f"{prefix}-{i:07d}"
            roll = rnd.random()

            if roll < malformed_rate:
                fh.write(f"{reference},TRANSFER,ACC0000001\n")          # columnas incompletas
                manifest.reject("MALFORMED_RECORD")
            elif roll < malformed_rate + duplicate_rate and valid_refs:
                # Se repite una referencia VÁLIDA: SmartOps solo marca como duplicada una
                # referencia que ya superó la validación de formato.
                row = valid_row(rnd, rnd.choice(valid_refs), value_date)
                writer.writerow(row.values())
                manifest.reject("DUPLICATE_REFERENCE")
            elif roll < malformed_rate + duplicate_rate + error_rate:
                row = valid_row(rnd, reference, value_date)
                code = rnd.choice(list(ERROR_KINDS))
                ERROR_KINDS[code](row, rnd)
                if code == "INVALID_DATE" and row["value_date"] == "":
                    code = "MISSING_FIELD"
                writer.writerow(row.values())  # csv.writer entrecomilla "12,5": llega como monto inválido
                manifest.reject(code)
            else:
                row = valid_row(rnd, reference, value_date)
                writer.writerow(row.values())
                valid_refs.append(reference)
                valid_for_statement.append(row)
            manifest.total_lines += 1

    manifest.expected_valid = len(valid_for_statement)

    stmt_path = out / f"statement_{prefix}.csv"
    with stmt_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh, lineterminator="\n")
        writer.writerow(STATEMENT_HEADER)
        for row in valid_for_statement:
            roll = rnd.random()
            if roll < missing_rate:
                manifest.expected_missing_external += 1
                continue
            amount = row["amount"]
            if roll < missing_rate + mismatch_rate:
                amount = f"{float(amount) + rnd.choice([-1, 1]) * rnd.randint(1, 500):.2f}"
                manifest.expected_amount_mismatch += 1
            else:
                manifest.expected_matched += 1
            writer.writerow([row["reference"], amount, row["currency"], row["value_date"]])
            manifest.statement_lines += 1
        for j in range(1, extra_external + 1):
            writer.writerow([f"{prefix}-EXT-{j:04d}", f"{rnd.randint(1_000, 50_000)}.00", "COP", value_date])
            manifest.expected_missing_internal += 1
            manifest.statement_lines += 1

    (out / f"manifest_{prefix}.json").write_text(json.dumps(manifest.__dict__, indent=2), encoding="utf-8")
    return manifest


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Genera archivos de operaciones y extractos para SmartOps")
    p.add_argument("--count", type=int, default=10_000, help="número de líneas de operaciones")
    p.add_argument("--error-rate", type=float, default=0.03, help="proporción de errores de negocio")
    p.add_argument("--duplicate-rate", type=float, default=0.005, help="proporción de referencias repetidas")
    p.add_argument("--malformed-rate", type=float, default=0.002, help="proporción de líneas mal formadas")
    p.add_argument("--missing-rate", type=float, default=0.01, help="válidas ausentes en el extracto")
    p.add_argument("--mismatch-rate", type=float, default=0.005, help="válidas con monto distinto en el extracto")
    p.add_argument("--extra-external", type=int, default=5, help="movimientos solo presentes en el extracto")
    p.add_argument("--prefix", default=datetime.now().strftime("GEN%Y%m%d%H%M%S"),
                   help="prefijo de referencias (único por corrida)")
    p.add_argument("--date", default=date.today().isoformat(), help="fecha valor (yyyy-MM-dd)")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--out", type=Path, default=Path("out"))
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    a = parse_args(sys.argv[1:] if argv is None else argv)
    m = generate(a.count, a.error_rate, a.duplicate_rate, a.malformed_rate, a.missing_rate, a.mismatch_rate,
                 a.extra_external, a.prefix, a.date, a.seed, a.out)
    rejected = sum(m.expected_rejected.values())
    print(f"Operaciones: {m.total_lines} líneas ({m.expected_valid} válidas, {rejected} con error)")
    print(f"Extracto:    {m.statement_lines} líneas -> esperado: {m.expected_matched} conciliadas, "
          f"{m.expected_amount_mismatch} con diferencia, {m.expected_missing_external} faltantes, "
          f"{m.expected_missing_internal} solo externas")
    print(f"Archivos en: {a.out.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
