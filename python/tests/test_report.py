SUMMARY = {
    "id": 3, "fileName": "extracto.csv", "valueDateFrom": "2026-11-19", "valueDateTo": "2026-11-19",
    "status": "COMPLETED", "startedBy": "operator", "totalInternal": 100, "totalExternal": 98,
    "matchedCount": 95, "amountMismatchCount": 2, "missingExternalCount": 3, "missingInternalCount": 1,
}


def test_match_rate_uses_union_of_both_sides(report):
    assert report.match_rate(SUMMARY) == 95 / 101
    assert report.match_rate({**SUMMARY, "totalInternal": 0, "missingInternalCount": 0}) == 0.0


def test_markdown_contains_summary_and_differences(report):
    differences = [{"reference": "R1", "result": "AMOUNT_MISMATCH", "internalAmount": 100,
                    "externalAmount": 90, "difference": 10}]
    md = report.render_markdown(SUMMARY, differences)
    assert "# Conciliación #3" in md
    assert "| Conciliadas | 95 |" in md
    assert "| R1 | AMOUNT_MISMATCH | 100 | 90 | 10 |" in md
    assert "**10.00**" in md


def test_markdown_without_differences(report):
    assert "concilia al 100 %" in report.render_markdown(SUMMARY, [])


def test_csv_export(report, tmp_path):
    path = tmp_path / "r.csv"
    report.write_csv(path, [{"reference": "R1", "result": "MISSING_INTERNAL", "externalAmount": 5, "extra": "x"}])
    assert path.read_text(encoding="utf-8").splitlines() == [
        "reference,result,internalAmount,externalAmount,difference,transactionId",
        "R1,MISSING_INTERNAL,,5,,",
    ]
