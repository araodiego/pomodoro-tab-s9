# Still Current

An original 25-minute ambient electronic composition in D Dorian at 120 BPM. Electric-piano tines carry a four-note motif over a slow-moving stereo pad, soft bass, and brushed percussion. The score, synthesis, stereo room impulse and percussion noise are generated in render.py. No external recordings or samples were used.

## Listen

Still-Current.flac is the separately delivered full listening master. A-control.flac and B-full-mix.flac in this kit use the same performance. A has no added modulation. In B, a 16 Hz cosine gain envelope is applied to the whole mix. The listening master applies that envelope to the pad. The two previews cover the same two-minute passage beginning at 00:32, with short fades added.

## Reproduce

Use Python 3.11, NumPy, SciPy and FFmpeg on the PATH. From a writable folder with at least 4 GB free, run python render.py, then python master.py. The first script synthesizes the score in two-second blocks with a fixed seed. The second writes the three stereo 48 kHz / 24-bit WAV masters, the AAC for Android, previews and measurements.json. To decode a supplied lossless file to a WAV, use ffmpeg -i INPUT.flac -c:a pcm_s24le OUTPUT.wav.

## Verification

All three WAVs and the decoded app soundtrack measured -20.00 LUFS integrated. The pad-only master and the decoded AAC both measured -6.60 dBTP. The app soundtrack is 25:00.000 long, with an average encoded audio bitrate of 248 kb/s.

Pulse analysis used 30-second excerpts starting at 01:20, 10:50 and 20:50, a 300–2,000 Hz band-pass filter and a Hann-windowed amplitude envelope. The decoded AAC's 16 Hz peak was 13.58 to 24.82 dB above the local median across the six channel/window measurements. See measurements.json for each master and channel. These are acoustic measurements. Task performance requires a separate listening experiment.

The Android build passed lint, 95 timer/Jira/meeting checks, and a timeline check covering all supported durations from 1 to 60 minutes. Its signing certificate matches 0.6. Physical Galaxy playback, an end-to-end listening evaluation, and live interruption behavior remain unverified.
