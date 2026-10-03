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

def ensure_packages():
    missing = []
    for name in ("torch", "numpy", "onnx"):
        try:
            __import__(name)
        except ImportError:
            missing.append(name)
    if missing:
        subprocess.check_call([
            sys.executable, "-m", "pip", "install", "--quiet",
            "torch>=2.9,<2.12", "numpy", "onnx>=1.17,<2"
        ])

def download(url: str, target: Path):
    import urllib.request
    print(f"Downloading {url} -> {target}")
    urllib.request.urlretrieve(url, target)

def main():
    ensure_packages()
    import torch

    repo = WORK / "heb-ocr"
    if not repo.exists():
        subprocess.check_call([
            "git", "clone", "--depth", "1",
            "https://github.com/itayinbarr/heb-ocr.git", str(repo)
        ])

    sys.path.insert(0, str(repo))
    checkpoint = WORK / "mishkefet-v1.pt"
    charset = ASSETS / "charset.json"

    if not checkpoint.exists():
        download(MODEL_URL, checkpoint)
    if not charset.exists():
        download(CHARSET_URL, charset)

    state = torch.load(checkpoint, map_location="cpu", weights_only=False)
    config = state.get("config", {})
    arch = config.get("arch", "htr_vt")
    if arch == "trocr":
        raise RuntimeError("Unexpected TrOCR checkpoint; this Android exporter expects the HTR-VT Mishkefet checkpoint.")

    from hebocr.models.htr_vt import build_model

    model = build_model(
        len(state["charset"]),
        config.get("size", "base"),
        mask_ratio=0.0,
    )
    model.load_state_dict(state["model"])
    model = model.cpu().eval()

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
