import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / ".model-build"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
ASSETS.mkdir(parents=True, exist_ok=True)
WORK.mkdir(parents=True, exist_ok=True)

MODEL_URL = "https://huggingface.co/itayinbar/Mishkefet-v1/resolve/main/mishkefet-v1.pt?download=true"
CHARSET_URL = "https://huggingface.co/itayinbar/Mishkefet-v1/resolve/main/charset.json?download=true"

def download(url: str, target: Path):
    import urllib.request
    print(f"Downloading {url} -> {target}")
    urllib.request.urlretrieve(url, target)

def main():
    try:
        import torch
        import numpy
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet",
                               "torch>=2.9,<2.12", "numpy"])
        import torch

    repo = WORK / "heb-ocr"
    if not repo.exists():
        subprocess.check_call(["git", "clone", "--depth", "1",
                               "https://github.com/itayinbarr/heb-ocr.git", str(repo)])

    sys.path.insert(0, str(repo))
    checkpoint = WORK / "mishkefet-v1.pt"
    charset = ASSETS / "charset.json"
    if not checkpoint.exists():
        download(MODEL_URL, checkpoint)
    if not charset.exists():
        download(CHARSET_URL, charset)

    from hebocr.recognize import Recognizer

    recognizer = Recognizer(checkpoint, device="cpu")
    model = recognizer.model.cpu().eval()

    dummy = torch.zeros(1, 1, 64, 2048, dtype=torch.float32)
    output = ASSETS / "mishkefet.onnx"

    print("Exporting ONNX...")
    torch.onnx.export(
        model,
        dummy,
        str(output),
        input_names=["image"],
        output_names=["log_probs"],
        opset_version=17,
        dynamo=False,
        do_constant_folding=True,
    )
    print(f"ONNX written: {output} ({output.stat().st_size / 1024 / 1024:.1f} MB)")

if __name__ == "__main__":
    main()
