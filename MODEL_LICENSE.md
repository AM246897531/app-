# Mishkefet-v1 model

This application build packages the Mishkefet-v1 Hebrew handwriting model from:

https://huggingface.co/itayinbar/Mishkefet-v1

The model card currently specifies the model weights license as CC-BY-NC-SA-4.0.

The CI process downloads the model only at build time and converts it into an ONNX asset bundled into the APK. The installed application performs inference locally and does not require network access.

For redistribution or commercial use, verify the current model license and attribution requirements before distributing the APK.
