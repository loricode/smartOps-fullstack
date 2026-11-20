"""Carga los scripts por ruta (sus carpetas tienen guiones y no son paquetes importables)."""
import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]


def _load(name: str, relative: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


@pytest.fixture(scope="session")
def generator():
    return _load("generate_operations", "file-generator/generate_operations.py")


@pytest.fixture(scope="session")
def validator():
    return _load("validate_file", "data-validator/validate_file.py")


@pytest.fixture(scope="session")
def report():
    return _load("reconciliation_report", "reports/reconciliation_report.py")
