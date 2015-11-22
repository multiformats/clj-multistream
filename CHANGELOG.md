Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

### Added
- Add `Encoder` and `Decoder` protocols to core with stream-based methods
  `encode!` and `decode!`, respectively.
- Add `encode` and `decode` to core which operate on byte arrays.
- Add `multicodec.codecs` namespace with a few useful codec implementations.

### Changed
- Rename `core/paths` to `core/headers`.
- Move header functions to new `multicodec.header` namespace.
- On validation errors, header functions now throw exceptions with type
  information via `ex-data`.

## [0.2.0] - 2015-11-19

### Added
- `multicodec.core/paths` provides a collection of standard codec paths to use
  for various encodings.

## 0.1.0 - 2015-11-19

Initial project release

[Unreleased]: https://github.com/greglook/clj-multicodec/compare/0.2.0...HEAD
[0.2.0]: https://github.com/greglook/clj-multicodec/compare/0.1.0...0.2.0
