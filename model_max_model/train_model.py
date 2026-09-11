"""Train the OpenTouch max_model classifier from a folder-per-class dataset.

The default run selects exactly 100 images per category from the source folder,
keeps 20% of those images for validation, and writes a CPU-loadable checkpoint
plus the Android preprocessing metadata into output/.
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

import torch
from PIL import ImageFile
from torch import nn
from torch.utils.data import DataLoader, Subset
from torchvision import datasets, models, transforms


SEED = 42
IMAGE_SIZE = 224
MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train OpenTouch max_model.")
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent / "output")
    parser.add_argument("--images-per-class", type=int, default=100)
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--no-pretrained", action="store_true")
    return parser.parse_args()


def choose_indices(dataset: datasets.ImageFolder, images_per_class: int) -> tuple[list[int], list[int]]:
    rng = random.Random(SEED)
    by_class: dict[int, list[int]] = {index: [] for index in range(len(dataset.classes))}
    for sample_index, (_, class_index) in enumerate(dataset.samples):
        by_class[class_index].append(sample_index)

    train_indices: list[int] = []
    validation_indices: list[int] = []
    for class_index, indices in by_class.items():
        if len(indices) < images_per_class:
            raise RuntimeError(
                f"Class '{dataset.classes[class_index]}' has {len(indices)} images; "
                f"{images_per_class} are required."
            )
        selected = rng.sample(indices, images_per_class)
        rng.shuffle(selected)
        validation_count = max(1, round(images_per_class * 0.2))
        validation_indices.extend(selected[:validation_count])
        train_indices.extend(selected[validation_count:])

    rng.shuffle(train_indices)
    rng.shuffle(validation_indices)
    return train_indices, validation_indices


def main() -> None:
    args = parse_args()
    if args.images_per_class < 2:
        raise ValueError("--images-per-class must be at least 2")

    ImageFile.LOAD_TRUNCATED_IMAGES = True
    random.seed(SEED)
    torch.manual_seed(SEED)
    args.output_dir.mkdir(parents=True, exist_ok=True)

    train_transform = transforms.Compose([
        transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(12),
        transforms.ColorJitter(brightness=0.15, contrast=0.15),
        transforms.ToTensor(),
        transforms.Normalize(mean=MEAN, std=STD),
    ])
    validation_transform = transforms.Compose([
        transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize(mean=MEAN, std=STD),
    ])

    train_source = datasets.ImageFolder(args.data_dir, transform=train_transform)
    validation_source = datasets.ImageFolder(args.data_dir, transform=validation_transform)
    if len(train_source.classes) < 2:
        raise RuntimeError("The data directory must contain at least two class folders.")
    if train_source.classes != validation_source.classes:
        raise RuntimeError("Training and validation class order does not match.")

    train_indices, validation_indices = choose_indices(train_source, args.images_per_class)
    print(f"Classes: {train_source.classes}")
    print(f"Selected {args.images_per_class} images per class")
    print(f"Training images: {len(train_indices)}; validation images: {len(validation_indices)}")

    train_loader = DataLoader(
        Subset(train_source, train_indices),
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=0,
    )
    validation_loader = DataLoader(
        Subset(validation_source, validation_indices),
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=0,
    )

    weights = None if args.no_pretrained else models.MobileNet_V3_Small_Weights.DEFAULT
    model = models.mobilenet_v3_small(weights=weights)
    for parameter in model.features.parameters():
        parameter.requires_grad = False
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(train_source.classes))

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on {device}")
    model.to(device)
    optimizer = torch.optim.Adam(model.classifier.parameters(), lr=0.001)
    loss_function = nn.CrossEntropyLoss()

    for epoch in range(args.epochs):
        model.train()
        running_loss = 0.0
        for images, labels in train_loader:
            images, labels = images.to(device), labels.to(device)
            optimizer.zero_grad()
            predictions = model(images)
            loss = loss_function(predictions, labels)
            loss.backward()
            optimizer.step()
            running_loss += loss.item() * images.size(0)

        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for images, labels in validation_loader:
                predictions = model(images.to(device)).argmax(dim=1)
                correct += (predictions == labels.to(device)).sum().item()
                total += labels.size(0)

        accuracy = correct / total if total else 0.0
        average_loss = running_loss / len(train_indices)
        print(
            f"epoch {epoch + 1:02d}/{args.epochs} "
            f"loss={average_loss:.4f} validation_accuracy={accuracy:.2%}"
        )

    checkpoint = {
        "model_state_dict": model.state_dict(),
        "classes": train_source.classes,
        "input_size": IMAGE_SIZE,
        "mean": MEAN,
        "std": STD,
    }
    torch.save(checkpoint, args.output_dir / "max_model_checkpoint.pth")
    (args.output_dir / "labels.txt").write_text(
        "\n".join(train_source.classes) + "\n", encoding="utf-8"
    )
    (args.output_dir / "model_config.json").write_text(
        json.dumps(
            {
                "input_width": IMAGE_SIZE,
                "input_height": IMAGE_SIZE,
                "color_order": "RGB",
                "mean": MEAN,
                "std": STD,
                "labels": train_source.classes,
                "output_type": "logits",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Saved checkpoint and metadata to {args.output_dir}")


if __name__ == "__main__":
    main()
