# Offline Handwriting Android

100% offline architecture: no INTERNET permission, no cloud API, no account, no remote inference.

The recognition engine is being integrated as bundled on-device models. The UI already provides a local handwriting canvas and Hebrew/English mode selection.

## Security invariant
The Android manifest intentionally contains no INTERNET permission. Recognition must remain on-device.

## Build
`gradle assembleDebug`

## Planned bundled engines
- Hebrew handwriting: Mishkefet-v1 (local model; model license must be respected).
- English handwriting: local CTC/ONNX handwriting model.
- Local segmentation, spacing, punctuation and contextual correction.
