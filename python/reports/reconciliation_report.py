#!/usr/bin/env python3
"""
Reporte de conciliación a partir del API de SmartOps.

Descarga el resumen de una conciliación y todas sus diferencias (lo que NO concilió)
y genera:
  * reconciliation_<id>.md   -> resumen ejecutivo legible
  * reconciliation_<id>.csv  -> detalle de diferencias para análisis en hoja de cálculo

Ejemplo:
    python reconciliation_report.py --id 1 --url http://localhost:8080 --user operator --password operator123
"""
from __future__ import annotations

import argparse
import base64
import csv
import json
import sys
import urllib.request
from pathlib import Path

DIFFERENCE_RESULTS = ("AMOUNT_MISMATCH", "MISSING_EXTERNAL", "MISSING_INTERNAL")


class SmartOpsClient:
    def __init__(self, base_url: str, user: str, password: str, timeout: float = 30):
        self.base_url = base_url.rstrip("/")
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}", "Accept": "application/json"}
        self.timeout = timeout

    def get(self, path: str) -> dict:
        req = urllib.request.Request(self.base_url + path, headers=self.headers)
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:  # noqa: S310 (URL configurada por el usuario)
            return json.loads(resp.read().decode("utf-8"))

    def paged(self, path: str, size: int = 200):
        page = 0
        sep = "&" if "?" in path else "?"
        while True:
            data = self.get(f"{path}{sep}page={page}&size={size}")
            yield from data["content"]
            if (page + 1) * size >= data["totalElements"]:
                break
            page += 1


def match_rate(summary: dict) -> float:
    universe = summary["totalInternal"] + summary["missingInternalCount"]
    return summary["matchedCount"] / universe if universe else 0.0


def render_markdown(summary: dict, differences: list[dict]) -> str:
    rate = match_rate(summary)
    lines = [
        f"# Conciliación #{summary['id']}",
        "",
        f"- **Extracto:** {summary['fileName']}",
        f"- **Rango de fechas valor:** {summary.get('valueDateFrom', '-')} a {summary.get('valueDateTo', '-')}",
        f"- **Estado:** {summary['status']}",
        f"- **Ejecutada por:** {summary['startedBy']}",
        f"- **Tasa de conciliación:** {rate:.2%}",
        "",
        "| Resultado | Registros |",
        "|---|---:|",
        f"| Conciliadas | {summary['matchedCount']} |",
        f"| Diferencia de monto | {summary['amountMismatchCount']} |",
        f"| Faltan en extracto externo | {summary['missingExternalCount']} |",
        f"| Faltan en SmartOps | {summary['missingInternalCount']} |",
        "",
    ]
    if differences:
        total_diff = sum(float(d.get("difference") or 0) for d in differences)
        lines += [f"Diferencia neta de montos (interno − externo): **{total_diff:,.2f}**", "",
                  "## Primeras diferencias", "", "| Referencia | Resultado | Interno | Externo | Diferencia |",
                  "|---|---|---:|---:|---:|"]
        for d in differences[:25]:
            lines.append(f"| {d['reference']} | {d['result']} | {d.get('internalAmount', '')} | "
                         f"{d.get('externalAmount', '')} | {d.get('difference', '')} |")
    else:
        lines.append("Sin diferencias: el extracto concilia al 100 %.")
    return "\n".join(lines) + "\n"


def write_csv(path: Path, differences: list[dict]) -> None:
    cols = ["reference", "result", "internalAmount", "externalAmount", "difference", "transactionId"]
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=cols, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        writer.writerows(differences)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Genera el reporte de una conciliación")
    p.add_argument("--id", type=int, required=True, help="id de la conciliación")
    p.add_argument("--url", default="http://localhost:8080")
    p.add_argument("--user", default="operator")
    p.add_argument("--password", default="operator123")
    p.add_argument("--out", type=Path, default=Path("out"))
    a = p.parse_args(sys.argv[1:] if argv is None else argv)

    client = SmartOpsClient(a.url, a.user, a.password)
    summary = client.get(f"/api/v1/reconciliations/{a.id}")
    differences = [d for result in DIFFERENCE_RESULTS
                   for d in client.paged(f"/api/v1/reconciliations/{a.id}/details?result={result}")]

    a.out.mkdir(parents=True, exist_ok=True)
    md_path = a.out / f"reconciliation_{a.id}.md"
    csv_path = a.out / f"reconciliation_{a.id}.csv"
    md_path.write_text(render_markdown(summary, differences), encoding="utf-8")
    write_csv(csv_path, differences)
    print(f"Tasa de conciliación {match_rate(summary):.2%} — {len(differences)} diferencias")
    print(f"Reporte: {md_path.resolve()}\nDetalle: {csv_path.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
