#!/usr/bin/env python3
"""Build an OpenTouch model package for import by the Android app.

The generated .opentouchmodel file is a ZIP archive with exactly these entries:

    model.onnx
    model.json

No third-party Python packages are required. The ONNX graph itself is treated as
an opaque file; model conversion/export should happen before running this tool.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any


REQUIRED_CONFIG_KEYS = ("input_width", "input_height", "mean", "std", "labels")
PACKAGE_ENTRIES = ("model.onnx", "model.json")


class ConversionError(ValueError):
    """An input cannot be packaged safely for the Android model runner."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Package an ONNX model and OpenTouch JSON config into .opentouchmodel."
    )
    parser.add_argument(
        "--model",
        required=True,
        type=Path,
        help="Path to the input .onnx file.",
    )
    parser.add_argument(
        "--config",
        required=True,
        type=Path,
        help="Path to the JSON model configuration file.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Output .opentouchmodel path (default: next to the ONNX file).",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Replace an existing output package.",
    )
    return parser.parse_args()


def require_file(path: Path, expected_suffix: str, description: str) -> Path:
    path = path.expanduser().resolve()
    if not path.is_file():
        raise ConversionError(f"{description} does not exist or is not a file: {path}")
    if path.suffix.lower() != expected_suffix:
        raise ConversionError(
            f"{description} must have a {expected_suffix} extension: {path.name}"
        )
    if path.stat().st_size == 0:
        raise ConversionError(f"{description} is empty: {path}")
    return path


def require_positive_int(config: dict[str, Any], key: str) -> None:
    value = config.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ConversionError(f"'{key}' must be a positive integer")


def require_float_array(config: dict[str, Any], key: str) -> None:
    values = config.get(key)
    if not isinstance(values, list) or len(values) != 3:
        raise ConversionError(f"'{key}' must contain exactly 3 RGB numbers")
    for value in values:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ConversionError(f"'{key}' must contain only numbers")
        if not math.isfinite(float(value)):
            raise ConversionError(f"'{key}' must contain finite numbers")
    if key == "std" and any(float(value) <= 0 for value in values):
        raise ConversionError("'std' values must be greater than zero")


def validate_config(config_path: Path) -> dict[str, Any]:
    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ConversionError(
            f"Invalid JSON in {config_path.name} at line {error.lineno}, column {error.colno}"
        ) from error
    except UnicodeDecodeError as error:
        raise ConversionError(f"Configuration is not UTF-8 JSON: {config_path}") from error

    if not isinstance(config, dict):
        raise ConversionError("The JSON configuration root must be an object")

    missing = [key for key in REQUIRED_CONFIG_KEYS if key not in config]
    if missing:
        raise ConversionError(f"Configuration is missing required field(s): {', '.join(missing)}")

    require_positive_int(config, "input_width")
    require_positive_int(config, "input_height")
    require_float_array(config, "mean")
    require_float_array(config, "std")

    labels = config["labels"]
    if not isinstance(labels, list) or not labels:
        raise ConversionError("'labels' must be a non-empty array")
    if any(not isinstance(label, str) or not label.strip() for label in labels):
        raise ConversionError("'labels' must contain non-empty strings")
    if len(set(labels)) != len(labels):
        raise ConversionError("'labels' must not contain duplicates")

    color_order = config.get("color_order", "RGB")
    if color_order != "RGB":
        raise ConversionError(
            "'color_order' must be 'RGB'; the Android runner reads RGB pixel planes"
        )

    output_type = config.get("output_type", "logits")
    if output_type != "logits":
        raise ConversionError(
            "'output_type' must be 'logits'; the Android runner applies softmax"
        )

    return config


def normalized_config(config: dict[str, Any]) -> bytes:
    """Return stable, readable JSON stored inside the package."""
    return (json.dumps(config, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def output_path(model_path: Path, requested: Path | None) -> Path:
    if requested is None:
        return model_path.with_suffix(".opentouchmodel")
    path = requested.expanduser().resolve()
    if path.suffix.lower() != ".opentouchmodel":
        raise ConversionError("Output must have the .opentouchmodel extension")
    return path


def write_package(model_path: Path, config_bytes: bytes, package_path: Path, force: bool) -> None:
    if package_path.exists() and not force:
        raise ConversionError(
            f"Output already exists: {package_path}\n"
            "Use --force if you really want to replace it."
        )
    package_path.parent.mkdir(parents=True, exist_ok=True)

    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=f".{package_path.stem}-",
            suffix=".tmp",
            dir=package_path.parent,
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)
        with zipfile.ZipFile(
            temporary_path, mode="w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
        ) as archive:
            archive.write(model_path, arcname="model.onnx")
            archive.writestr("model.json", config_bytes)

        with zipfile.ZipFile(temporary_path, mode="r") as archive:
            if archive.testzip() is not None:
                raise ConversionError("The generated package failed ZIP integrity verification")
            names = tuple(archive.namelist())
            if names != PACKAGE_ENTRIES:
                raise ConversionError(
                    f"Generated package has unexpected entries: {', '.join(names)}"
                )
        os.replace(temporary_path, package_path)
        temporary_path = None
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def main() -> int:
    args = parse_args()
    try:
        model_path = require_file(args.model, ".onnx", "Model")
        config_path = require_file(args.config, ".json", "Configuration")
        config = validate_config(config_path)
        package_path = output_path(model_path, args.output)
        write_package(model_path, normalized_config(config), package_path, args.force)
    except (ConversionError, OSError, zipfile.BadZipFile) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Created: {package_path}")
    print(f"Contents: {', '.join(PACKAGE_ENTRIES)}")
    print(f"Labels: {', '.join(config['labels'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
