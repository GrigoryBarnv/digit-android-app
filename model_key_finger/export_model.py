"""Export the trained key/finger classifier for Android ONNX Runtime."""

from pathlib import Path
import json

import torch
from torch import nn
from torchvision import models


ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT / "output"
CHECKPOINT_PATH = OUTPUT_DIR / "key_finger_checkpoint.pth"


class KeyFingerOutputOrder(nn.Module):
    """Reorder ImageFolder's [finger, key] logits to [key, finger]."""

    def __init__(self, classifier):
        super().__init__()
        self.classifier = classifier

    def forward(self, image):
        logits = self.classifier(image)
        return logits[:, [1, 0]]


def main():
    checkpoint = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=False)
    trained_classes = checkpoint["classes"]
    if trained_classes != ["finger", "key"]:
        raise RuntimeError(f"Expected training classes ['finger', 'key'], found {trained_classes}")

    model = models.mobilenet_v3_small(weights=None)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, 2)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    export_model = KeyFingerOutputOrder(model)
    example_input = torch.randn(1, 3, 224, 224, dtype=torch.float32)
    onnx_path = OUTPUT_DIR / "key_finger.onnx"

    export_arguments = {
        "input_names": ["input"],
        "output_names": ["logits"],
        "opset_version": 17,
    }
    try:
        torch.onnx.export(
            export_model,
            (example_input,),
            str(onnx_path),
            dynamo=True,
            **export_arguments,
        )
    except (ImportError, ModuleNotFoundError):
        # Older installations may not include the optional onnxscript package.
        torch.onnx.export(
            export_model,
            (example_input,),
            str(onnx_path),
            dynamo=False,
            **export_arguments,
        )

    labels = ["key", "finger"]
    (OUTPUT_DIR / "labels.txt").write_text("\n".join(labels) + "\n", encoding="utf-8")
    (OUTPUT_DIR / "model_config.json").write_text(
        json.dumps(
            {
                "input_width": 224,
                "input_height": 224,
                "color_order": "RGB",
                "mean": [0.485, 0.456, 0.406],
                "std": [0.229, 0.224, 0.225],
                "labels": labels,
                "output_type": "logits",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Exported: {onnx_path}")
    print("Labels: key, finger")


if __name__ == "__main__":
    main()
