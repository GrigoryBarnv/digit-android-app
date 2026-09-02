"""Train a small two-class classifier for the key/finger proof of concept."""

from pathlib import Path
import json
import random

import torch
from PIL import ImageFile
from torch import nn
from torch.utils.data import DataLoader, Subset
from torchvision import datasets, models, transforms


ROOT = Path(__file__).resolve().parent
DATASET_DIR = ROOT / "dataset"
OUTPUT_DIR = ROOT / "output"
SEED = 42


def split_per_class(dataset: datasets.ImageFolder):
    """Keep one image from each class for validation when the dataset is tiny."""
    random.seed(SEED)
    by_class = {class_index: [] for class_index in range(len(dataset.classes))}
    for index, (_, class_index) in enumerate(dataset.samples):
        by_class[class_index].append(index)

    train_indices = []
    validation_indices = []
    for indices in by_class.values():
        random.shuffle(indices)
        validation_indices.append(indices[0])
        train_indices.extend(indices[1:])
    random.shuffle(train_indices)
    return train_indices, validation_indices


def main():
    ImageFile.LOAD_TRUNCATED_IMAGES = True
    torch.manual_seed(SEED)
    OUTPUT_DIR.mkdir(exist_ok=True)

    image_size = 224
    weights = models.MobileNet_V3_Small_Weights.DEFAULT
    normalize = transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225],
    )
    train_transform = transforms.Compose([
        transforms.Resize((image_size, image_size)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(12),
        transforms.ColorJitter(brightness=0.15, contrast=0.15),
        transforms.ToTensor(),
        normalize,
    ])
    validation_transform = transforms.Compose([
        transforms.Resize((image_size, image_size)),
        transforms.ToTensor(),
        normalize,
    ])

    train_source = datasets.ImageFolder(DATASET_DIR, transform=train_transform)
    validation_source = datasets.ImageFolder(DATASET_DIR, transform=validation_transform)
    if train_source.classes != ["finger", "key"]:
        raise RuntimeError(
            f"Expected class folders named finger and key, found {train_source.classes}"
        )
    if len(train_source) < 4:
        raise RuntimeError("At least four images are needed for this proof of concept.")

    train_indices, validation_indices = split_per_class(train_source)
    train_loader = DataLoader(
        Subset(train_source, train_indices), batch_size=4, shuffle=True, num_workers=0
    )
    validation_loader = DataLoader(
        Subset(validation_source, validation_indices), batch_size=2, shuffle=False, num_workers=0
    )

    model = models.mobilenet_v3_small(weights=weights)
    for parameter in model.features.parameters():
        parameter.requires_grad = False
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(train_source.classes))

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)
    optimizer = torch.optim.Adam(model.classifier.parameters(), lr=0.001)
    loss_function = nn.CrossEntropyLoss()

    epochs = 15
    for epoch in range(epochs):
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
        validation_accuracy = correct / total if total else 0.0
        average_loss = running_loss / len(train_indices)
        print(
            f"epoch {epoch + 1:02d}/{epochs} "
            f"loss={average_loss:.4f} validation_accuracy={validation_accuracy:.2%}"
        )

    checkpoint = {
        "model_state_dict": model.state_dict(),
        "classes": train_source.classes,
        "input_size": image_size,
        "mean": [0.485, 0.456, 0.406],
        "std": [0.229, 0.224, 0.225],
    }
    torch.save(checkpoint, OUTPUT_DIR / "key_finger_checkpoint.pth")
    (OUTPUT_DIR / "labels.txt").write_text("\n".join(train_source.classes) + "\n", encoding="utf-8")
    (OUTPUT_DIR / "model_config.json").write_text(
        json.dumps(
            {
                "input_width": image_size,
                "input_height": image_size,
                "color_order": "RGB",
                "mean": [0.485, 0.456, 0.406],
                "std": [0.229, 0.224, 0.225],
                "labels": train_source.classes,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Saved checkpoint and metadata to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
