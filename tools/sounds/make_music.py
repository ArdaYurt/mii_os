# Synthesizes Mii_OS's original home-screen music: a calm, seamless loop (no samples).
# Regenerate: python3 tools/sounds/make_music.py out.wav && ffmpeg -i out.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/mii_music.ogg
import numpy as np, wave, sys

SR = 32000
BPM = 84
BEAT = 60 / BPM
BARS = 16
LENGTH = int(SR * BEAT * 4 * BARS)
out = np.zeros(LENGTH)

def note(midi): return 440.0 * 2 ** ((midi - 69) / 12)

def add(start_beat, sound, gain):
    # wraps around the end, so the loop is seamless
    i = int(start_beat * BEAT * SR) % LENGTH
    n = len(sound)
    first = min(n, LENGTH - i)
    out[i:i + first] += gain * sound[:first]
    if n > first:
        out[:n - first] += gain * sound[first:]

def keys(f, beats):
    # soft electric piano: sine plus a little bell, gentle decay
    t = np.arange(int(SR * BEAT * beats + SR * 1.2)) / SR
    env = np.exp(-t / 1.1) * np.clip(t / 0.006, 0, 1)
    return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / 0.3)) * env

def pad(f, beats):
    t = np.arange(int(SR * BEAT * beats)) / SR
    d = len(t) / SR
    env = np.clip(t / 0.8, 0, 1) * np.clip((d - t) / 0.8, 0, 1)
    s = sum(np.sin(2 * np.pi * f * (1 + det) * t) for det in (-0.003, 0, 0.003)) / 3
    return s * env

def bell(f):
    t = np.arange(int(SR * 2.0)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t / 0.2)) * np.exp(-t / 0.6) * np.clip(t / 0.003, 0, 1)

def bass(f, beats):
    t = np.arange(int(SR * BEAT * beats)) / SR
    d = len(t) / SR
    return np.sin(2 * np.pi * f * t) * np.clip(t / 0.02, 0, 1) * np.clip((d - t) / 0.1, 0, 1) * np.exp(-t / 1.5)

# F major: Fmaj7 | Dm9 | Bbmaj7 | C6sus, two bars each, played twice with different melodies
chords = [
    (41, [53, 57, 60, 64]),  # Fmaj7
    (38, [53, 57, 60, 64]),  # Dm9 voiced over D
    (46, [53, 57, 62, 65]),  # Bbmaj7 (6/9 colour)
    (48, [52, 55, 57, 62]),  # C6sus
]
melody_a = [(0, 72), (1.5, 69), (2, 67), (3, 69), (4, 72), (6, 74), (8, 72), (9.5, 69), (10, 65), (12, 67), (14, 69)]
melody_b = [(0, 76), (1, 74), (2, 72), (4, 69), (5.5, 72), (6, 74), (8, 72), (9, 69), (10, 67), (11, 65), (12, 64), (14, 65)]

for rep in range(2):
    for ci, (root, voicing) in enumerate(chords):
        bar0 = rep * 8 + ci * 2
        b = bar0 * 4
        for v in voicing:
            add(b, pad(note(v), 8), 0.06)
        # gentle comping: chord on beat 1 and the "and" of 2, each bar
        for off in (0, 2.5, 4, 6.5):
            for v in voicing:
                add(b + off, keys(note(v + 12), 1.5), 0.045)
        for off, dur in ((0, 3), (3, 1), (4, 3), (7, 1)):
            add(b + off, bass(note(root), dur), 0.22)
    mel = melody_a if rep == 0 else melody_b
    for beat, m in mel:
        for k in range(2):  # each melody plays twice over its eight bars
            add(rep * 32 + k * 16 + beat, bell(note(m + 12)), 0.08)

out /= np.max(np.abs(out)) + 1e-9
out *= 0.6
data = (out * 32767).astype('<i2')
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())
