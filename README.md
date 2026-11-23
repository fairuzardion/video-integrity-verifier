# video-integrity-verifier

Verifies video integrity with **time-segmented SHA-256 hash chains**, **perceptual fingerprints (dHash + Hamming distance)** and a
**digital signature (Ed25519 or ECDSA P-256, Bouncy Castle)**. It tells you whether a video is the signed original, a
re-encode of it, or tampered with - and for tampering it pinpoints *which segments and which time ranges* were cut,
inserted, shifted or visually altered.

Java 21 · Spring Boot 3.3 · Maven · FFmpeg · Bouncy Castle · Spring Data JPA (PostgreSQL / H2) · JUnit 5 + Mockito

## 1. How it works

```
 video ──ffprobe──> duration ──> fixed windows [0,5) [5,10) ...       (segmenter)
   each window ──ffmpeg──> frames sampled at N fps, scaled to W px     (segmenter)
   each frame  ──> pixel SHA-256  +  64-bit dHash                      (fingerprint)
   segment     = SHA-256 over its frame pixel digests  +  concatenated dHashes
   chain       : chainHash[i] = SHA256(prevHash | i | startMs | endMs | segmentSha256 | perceptualHash)
   manifest    = metadata + sampling params + chain  ──canonical JSON──> Ed25519/ECDSA signature   (chain)
```

* **Hash chain** - every link includes the previous link, so changing, removing, inserting or reordering any segment
  changes every later `chainHash`.
* **Strict hash vs. perceptual hash** - the segment SHA-256 flips on *any* pixel change (including re-encoding). The
  dHash only changes when the *picture* changes: re-encodes stay within a few bits, different content differs by ~32 of
  64 bits. The per-segment distance is the **maximum** per-frame Hamming distance, so one swapped frame is not averaged away.
  `integrity.verifier.hamming-threshold` (default 10) separates "re-encoded" from "visually altered".
* **Signature** - the manifest is signed over a canonical JSON form (sorted keys, compact). The verifier only trusts the
  *configured public key*; the manifest carries just an algorithm label and key id, which must match.
* **Verification order** (`ManifestVerifier`): (1) signature -> (2) manifest's own chain consistency -> (3) whole-file SHA-256
  (identical => `AUTHENTIC` immediately) -> (4) re-segment the test video with the *manifest's* parameters, fingerprint,
  **align** reference and test segment sequences (edit-distance alignment, `SegmentAligner`) and turn the alignment into anomalies.

The alignment is what lets a cut or insertion be reported as such instead of making every following segment "different":

| Alignment result | Anomaly | Meaning |
|---|---|---|
| reference segments with no counterpart | `SEGMENT_MISSING` | cut / trim (head, middle or tail is stated) |
| test segments with no counterpart | `SEGMENT_INSERTED` | splice / insertion / appended content |
| matched but perceptual distance > threshold | `SEGMENT_ALTERED` | visual content changed in place |
| matched, bytes differ, distance <= threshold | `SEGMENT_REENCODED` | re-encode / mild processing |
| matched at a different index | `TIMESTAMP_SHIFT` | content moved on the timeline (shift in seconds) |
| duration beyond tolerance | `DURATION_MISMATCH` | informational + critical |
| first diverging chain hash | `CHAIN_BREAK` | informational pointer to where the strict chain breaks |

**Verdicts**: `AUTHENTIC` (byte-identical, or identical decoded content), `MODIFIED_OR_REENCODED` (only `WARNING`s),
`TAMPERED` (any `CRITICAL` anomaly), `INVALID_SIGNATURE` (bad signature, untrusted key, or inconsistent reference chain).

### Granularity and limits (read this)
* Localization is **segment-granular** (default 5 s) on a **sampling grid** (default 2 fps). Smaller `segment-seconds` and
  higher `sample-fps` give finer localization at higher cost. Changes between sampled frames are not seen.
* A cut that is not a multiple of the sampling grid shifts all later samples by a sub-grid offset; those segments then
  compare as similar/altered rather than exact, but the first diverging segment and the duration delta still point at the cut.
* Near-static footage makes different segments perceptually alike, so after a *re-encode* a cut cannot always be localized
  exactly (the byte-exact SHA only anchors lossless/identical decodes). The alignment prefers the smallest distances to
  resolve this, but ambiguity is inherent.
* Small localized edits (e.g. a tiny watermark) may stay under the Hamming threshold and be classed as re-encoding; lower
  the threshold for stricter checks (re-encodes of your own pipeline then need a higher one).
* Audio is not analyzed (video stream only).
* The alignment matrix is O(n*m); above `max-alignment-cells` it falls back to positional comparison (logged).

## 2. Project layout (`com.noexperience.integrity`)

| Package | Content |
|---|---|
| `api` | `VideoController`, `ManifestController`, `ReportController`, DTOs, `GlobalExceptionHandler` |
| `segmenter` | `VideoSegmenter` interface, `FfmpegVideoSegmenter`, `ProcessRunner` (safe `ProcessBuilder`: timeout, exit codes, output cap), `SegmentPlanner`, `TempWorkspace` |
| `fingerprint` | `Sha256`, `PerceptualHasher` (dHash, Hamming), `SegmentPerceptualHash`, `SegmentFingerprinter`, `VideoAnalyzer` |
| `chain` | `HashChain`, `Manifest`, `SignedManifest`, `CanonicalJson`, `ManifestSigner`, `ManifestSignatureVerifier`, `PemKeys`, `ConfiguredKeyProvider` |
| `verifier` | `ManifestVerifier`, `SegmentAligner`, `Anomaly`, `VerificationReport`, `Verdict` |
| `storage` | JPA entities, repositories, `ManifestStore`/`ReportStore` abstractions + JPA implementations, `JsonCodec` (strict JSON import/export) |
| `service` | `RegistrationService`, `ManifestService`, `VerificationService` |
| `cli` | `CliArguments`, `IntegrityCliRunner`, `ReportFormatter` |
| `config` | `IntegrityProperties`, `IntegrityConfig` |

## 3. Prerequisites

* JDK 21+, Maven 3.9+
* **FFmpeg and FFprobe** on `PATH` (or set `FFMPEG_PATH` / `FFPROBE_PATH`): `ffmpeg -version`
* PostgreSQL for the server (`docker compose up -d`), or use the `dev` profile (in-memory H2)

## 4. Build, test, run

```bash
mvn test                                                    # no FFmpeg or database required
mvn spring-boot:run -Dspring-boot.run.profiles=dev          # REST API, in-memory H2
docker compose up -d && mvn spring-boot:run                 # REST API, PostgreSQL (see application.yml)
mvn -DskipTests package                                     # target/video-integrity-verifier.jar
```

### Configuration (`application.yml`, all overridable by environment variables)

| Property | Env var | Default |
|---|---|---|
| `integrity.ffmpeg.binary` / `probe-binary` | `FFMPEG_PATH` / `FFPROBE_PATH` | `ffmpeg` / `ffprobe` |
| `integrity.ffmpeg.timeout-seconds` | `FFMPEG_TIMEOUT_SECONDS` | 300 (per process) |
| `integrity.ffmpeg.temp-dir` | `INTEGRITY_TEMP_DIR` | system temp |
| `integrity.segmentation.segment-seconds` | `SEGMENT_SECONDS` | 5 |
| `integrity.segmentation.sample-fps` | `SAMPLE_FPS` | 2 |
| `integrity.segmentation.analysis-width` | `ANALYSIS_WIDTH` | 320 |
| `integrity.verifier.hamming-threshold` | `HAMMING_THRESHOLD` | 10 (of 64 bits, per frame) |
| `integrity.verifier.duration-tolerance-seconds` | `DURATION_TOLERANCE_SECONDS` | 0.5 |
| `integrity.verifier.max-alignment-cells` | `MAX_ALIGNMENT_CELLS` | 4000000 |
| `integrity.keys.algorithm` | `SIGNATURE_ALGORITHM` | `ed25519` (or `ecdsa-p256`) |
| `integrity.keys.private-key-path` / `public-key-path` | `INTEGRITY_PRIVATE_KEY` / `INTEGRITY_PUBLIC_KEY` | `./keys/integrity-{private,public}.pem` |
| `integrity.keys.keystore-path`, `keystore-password`, `key-alias` | `INTEGRITY_KEYSTORE`, `INTEGRITY_KEYSTORE_PASSWORD`, `INTEGRITY_KEY_ALIAS` | PKCS#12 alternative to PEM |
| `integrity.keys.auto-generate` | `INTEGRITY_KEY_AUTOGENERATE` | `true` (generates a pair if none exists) |
| datasource | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JPA_DDL_AUTO` | local PostgreSQL, `update` |

Keys: unencrypted PKCS#8 `PRIVATE KEY` and `PUBLIC KEY` PEM files. A **verify-only** deployment needs just the public key.
Set `auto-generate=false` in production so a missing key is an error, not a silently new identity.

## 5. CLI (no web server)

When the first argument is `sign`, `verify`, `keygen` or `help`, the app starts in CLI mode (no server, `cli` profile).
Spring overrides use the `=` form.

```bash
JAR=target/video-integrity-verifier.jar
java -jar $JAR keygen --algorithm ed25519                      # writes ./keys/integrity-{private,public}.pem
java -jar $JAR sign   --input original.mp4 --output original.manifest.json [--segment-seconds 5 --sample-fps 2]
java -jar $JAR verify --input suspect.mp4 --manifest original.manifest.json [--report report.json] [--json]
java -jar $JAR verify --input suspect.mp4 --manifest m.json --integrity.ffmpeg.binary=/opt/ffmpeg/bin/ffmpeg
# via Maven:
mvn spring-boot:run -Dspring-boot.run.arguments="sign --input original.mp4"
```
Exit codes: `0` AUTHENTIC · `1` MODIFIED_OR_REENCODED · `2` TAMPERED · `3` INVALID_SIGNATURE · `64` usage error · `70` failure.

## 6. REST API

```bash
# register: segment + fingerprint + chain + sign + store   (optional: -F segmentSeconds=5)
curl -F file=@original.mp4 http://localhost:8080/api/v1/videos/register

# export the signed manifest JSON
curl http://localhost:8080/api/v1/manifests/<manifestId> -o original.manifest.json

# verify against a stored manifest ...
curl -F file=@suspect.mp4 -F manifestId=<manifestId> http://localhost:8080/api/v1/videos/verify
# ... or against an exported manifest file
curl -F file=@suspect.mp4 -F manifest=@original.manifest.json http://localhost:8080/api/v1/videos/verify

curl http://localhost:8080/api/v1/reports/<reportId>             # stored report
curl http://localhost:8080/api/v1/manifests/<manifestId>/reports # report history
curl -H 'Content-Type: application/json' --data @original.manifest.json \
     http://localhost:8080/api/v1/manifests/import              # import (rejected unless signature+chain are valid)
```
Errors share one body: `{timestamp,status,error,message,path}` (400 invalid input, 404 unknown id, 413 too large, 422 unusable media).
Processing is synchronous; large uploads are limited by `MAX_UPLOAD_SIZE` (default 2GB).

Example report fragment (a 5 s section cut out of the middle of a 20 s video):
```json
{ "verdict": "TAMPERED",
  "anomalies": [
    {"type":"DURATION_MISMATCH","severity":"CRITICAL","shiftSec":-5.0},
    {"type":"SEGMENT_MISSING","severity":"CRITICAL","referenceSegments":[1],"referenceStartSec":5.0,"referenceEndSec":10.0,
     "description":"Reference segment(s) 1 (5.000s-10.000s) are missing from the test video: a section was cut out of the middle."},
    {"type":"TIMESTAMP_SHIFT","severity":"CRITICAL","referenceSegments":[2,3],"testSegments":[1,2],"shiftSec":-5.0} ] }
```

## 7. Test scenarios

Automated (`mvn test`, uses a synthetic segmenter, no FFmpeg): original, byte-different remux, re-encode, middle cut,
head trim, tail trim, splice, in-place alteration, forged manifest, untrusted key, validly-signed-but-inconsistent chain;
plus dHash/Hamming, hash-chain tamper detection, signing for both algorithms, PEM/key loading, aligner, FFmpeg command
construction (mocked runner), temp-file cleanup, CLI parsing.

Manual with real media (generate material, then `sign` the original and `verify` each variant):
```bash
F="ffmpeg -y -loglevel error"
$F -f lavfi -i mandelbrot=size=320x240:rate=25 -t 20 -pix_fmt yuv420p -c:v libx264 -g 25 original.mp4
$F -i original.mp4 -c:v libx264 -crf 38 -vf scale=256:192 reencoded.mp4                  # -> MODIFIED_OR_REENCODED
$F -i original.mp4 -filter_complex "[0:v]trim=0:5,setpts=PTS-STARTPTS[a];[0:v]trim=10:20,setpts=PTS-STARTPTS[b];[a][b]concat=n=2:v=1[o]" \
   -map "[o]" -c:v libx264 -pix_fmt yuv420p cut.mp4                                       # -> TAMPERED, segment 1 missing (5-10 s)
$F -i original.mp4 -f lavfi -i "life=size=320x240:rate=25,format=yuv420p" \
   -filter_complex "[0:v]trim=0:5,setpts=PTS-STARTPTS[a];[1:v]trim=0:5,setpts=PTS-STARTPTS[x];[0:v]trim=5:20,setpts=PTS-STARTPTS[b];[a][x][b]concat=n=3:v=1[o]" \
   -map "[o]" -c:v libx264 -pix_fmt yuv420p splice.mp4                                    # -> TAMPERED, test segment 1 inserted
$F -ss 5 -i original.mp4 -c:v libx264 -pix_fmt yuv420p trimmed.mp4                        # -> TAMPERED, segment 0 missing
```

## 8. Security notes
* Only the configured public key is trusted; manifests are never self-certifying. Rotating keys invalidates old manifests (key id mismatch).
* Imported manifest JSON is parsed strictly (unknown fields rejected) and the signature is computed over a re-serialization, not the received text.
* FFmpeg is launched with an argument list (no shell), stdin closed, timeouts and output caps; uploads are stored under random temp names and deleted afterwards.
* There is no authentication on the REST API; put it behind your gateway.
