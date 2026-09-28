"""Export max_model to ONNX for OpenTouch."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
from torch import nn
from torchvision import models


IMAGE_SIZE = 224


class MaxModelOutput(nn.Module):
    def __init__(self, model: nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        return self.model(image)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export max_model for OpenTouch.")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent / "output")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    checkpoint_path = args.output_dir / "max_model_checkpoint.pth"
    config_path = args.output_dir / "model_config.json"
    onnx_path = args.output_dir / "max_model.onnx"
    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    classes = checkpoint["classes"]

    model = models.mobilenet_v3_small(weights=None)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(classes))
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()
    export_model = MaxModelOutput(model).eval()

    try:
        torch.onnx.export(
            export_model,
            (torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE),),
            str(onnx_path),
            input_names=["input"],
            output_names=["logits"],
            opset_version=18,
            dynamo=True,
            external_data=False,
        )
    except (ImportError, ModuleNotFoundError):
        torch.onnx.export(
            export_model,
            (torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE),),
            str(onnx_path),
            input_names=["input"],
            output_names=["logits"],
            opset_version=18,
            dynamo=False,
            external_data=False,
        )

    config = json.loads(config_path.read_text(encoding="utf-8"))
    config["labels"] = classes
    config["output_type"] = "logits"
    config_path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {onnx_path}")


if __name__ == "__main__":
    main()
