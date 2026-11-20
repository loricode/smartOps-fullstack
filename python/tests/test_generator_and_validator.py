import csv
import json

ACCOUNT_DEPENDENT = {"ACCOUNT_NOT_FOUND", "ACCOUNT_BLOCKED"}  # el validador previo no conoce las cuentas


def _generate(generator, tmp_path, **overrides):
    params = dict(count=2000, error_rate=0.05, duplicate_rate=0.01, malformed_rate=0.005, missing_rate=0.02,
                  mismatch_rate=0.01, extra_external=3, prefix="TST", value_date="2026-11-19", seed=7,
                  out=tmp_path)
    params.update(overrides)
    return generator.generate(**params)


def test_generation_is_deterministic(generator, tmp_path):
    a = _generate(generator, tmp_path / "a")
    b = _generate(generator, tmp_path / "b")
    assert (tmp_path / "a" / "operations_TST.csv").read_bytes() == (tmp_path / "b" / "operations_TST.csv").read_bytes()
    assert a.__dict__ == b.__dict__


def test_manifest_counts_are_consistent(generator, tmp_path):
    m = _generate(generator, tmp_path)
    assert m.total_lines == 2000
    assert m.expected_valid + sum(m.expected_rejected.values()) == m.total_lines
    assert m.expected_matched + m.expected_amount_mismatch + m.expected_missing_external == m.expected_valid
    assert m.statement_lines == m.expected_matched + m.expected_amount_mismatch + m.expected_missing_internal
    saved = json.loads((tmp_path / "manifest_TST.json").read_text(encoding="utf-8"))
    assert saved["expected_valid"] == m.expected_valid


def test_valid_rows_respect_type_account_rules(generator, tmp_path):
    _generate(generator, tmp_path, error_rate=0, duplicate_rate=0, malformed_rate=0)
    with (tmp_path / "operations_TST.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            has_source, has_target = bool(row["source_account"]), bool(row["target_account"])
            expected = {"TRANSFER": (True, True), "PAYMENT": (True, False), "COLLECTION": (False, True)}
            assert (has_source, has_target) == expected[row["type"]]


def test_validator_detects_exactly_the_injected_format_errors(generator, validator, tmp_path):
    m = _generate(generator, tmp_path)
    report = validator.validate(tmp_path / "operations_TST.csv", "operations")

    expected = {k: v for k, v in m.expected_rejected.items() if k not in ACCOUNT_DEPENDENT}
    assert report["valid_header"] is True
    assert report["total_records"] == m.total_lines
    assert report["errors_by_code"] == expected


def test_validator_accepts_clean_statement(generator, validator, tmp_path):
    m = _generate(generator, tmp_path)
    report = validator.validate(tmp_path / "statement_TST.csv", "statement")
    assert report["invalid_records"] == 0
    assert report["total_records"] == m.statement_lines


def test_validator_rejects_wrong_header(validator, tmp_path):
    bad = tmp_path / "bad.csv"
    bad.write_text("ref,amount\nX,1\n", encoding="utf-8")
    report = validator.validate(bad, "statement")
    assert report["valid_header"] is False
    assert validator.main([str(bad), "--kind", "statement"]) == 1


def test_validator_exit_code_honours_threshold(generator, validator, tmp_path, capsys):
    _generate(generator, tmp_path, error_rate=0.2)
    path = str(tmp_path / "operations_TST.csv")
    assert validator.main([path, "--max-error-rate", "0.05"]) == 1
    assert validator.main([path, "--max-error-rate", "0.5"]) == 0
