"""Compare PyTorch and ONNX predictions for one saved image."""

from pathlib import Path
import argparse

import numpy as np
import onnxruntime as ort
from PIL import Image
import torch
from torchvision import models, transforms


ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT / "output"


def probabilities(logits):
    return torch.softmax(torch.as_tensor(logits), dim=1).numpy()[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "image",
        nargs="?",
        default=str(ROOT / "dataset" / "key" / "key.jpg"),
        help="Path to a saved key or finger image.",
    )
    args = parser.parse_args()

    checkpoint_path = OUTPUT_DIR / "key_finger_checkpoint.pth"
    onnx_path = OUTPUT_DIR / "key_finger.onnx"
    if not checkpoint_path.exists() or not onnx_path.exists():
        raise FileNotFoundError(
            "Train and export first. Expected output/key_finger_checkpoint.pth and output/key_finger.onnx."
        )

    image = Image.open(args.image).convert("RGB")
    preprocess = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ])
    input_tensor = preprocess(image).unsqueeze(0)

    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    model = models.mobilenet_v3_small(weights=None)
    model.classifier[3] = torch.nn.Linear(model.classifier[3].in_features, 2)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()
    with torch.no_grad():
        # Training used ImageFolder's order [finger, key]. Reorder to [key, finger].
        pytorch_logits = model(input_tensor)[:, [1, 0]]
    pytorch_probabilities = probabilities(pytorch_logits)

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    onnx_logits = session.run(
        [session.get_outputs()[0].name],
        {session.get_inputs()[0].name: input_tensor.numpy().astype(np.float32)},
    )[0]
    onnx_probabilities = probabilities(onnx_logits)

    labels = ["key", "finger"]
    pytorch_class = labels[int(np.argmax(pytorch_probabilities))]
    onnx_class = labels[int(np.argmax(onnx_probabilities))]
    max_difference = np.max(np.abs(pytorch_probabilities - onnx_probabilities))

    print(f"Image: {args.image}")
    print(f"PyTorch: {pytorch_class} | key={pytorch_probabilities[0]:.6f} finger={pytorch_probabilities[1]:.6f}")
    print(f"ONNX:    {onnx_class} | key={onnx_probabilities[0]:.6f} finger={onnx_probabilities[1]:.6f}")
    print(f"Maximum probability difference: {max_difference:.8f}")
    print("MATCH" if pytorch_class == onnx_class else "MISMATCH")


if __name__ == "__main__":
    main()
