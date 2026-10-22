# Synthesizes Mii_OS's original UI sounds (no samples from anywhere).
# Regenerate: python3 tools/sounds/make_sounds.py app/src/main/res/raw
import numpy as np, wave, sys, os
SR = 44100
out = sys.argv[1]

def t(d): return np.arange(int(SR * d)) / SR

def env(n, attack=0.004, decay=0.08):
    x = np.arange(n) / SR
    a = np.clip(x / attack, 0, 1)
    return a * np.exp(-x / decay)

def bell(f, d, decay, partials=((1, 1.0), (2.0, 0.25), (3.01, 0.08), (4.2, 0.04))):
    x = t(d)
    s = sum(a * np.sin(2 * np.pi * f * m * x) * np.exp(-x / (decay / m ** 0.6)) for m, a in partials)
    return s * np.clip(x / 0.003, 0, 1)

def glide(f0, f1, d, decay):
    x = t(d)
    f = f0 * (f1 / f0) ** (x / d)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.18 * np.sin(2 * ph)) * env(len(x), 0.004, decay)

def mix(*parts):
    n = max(len(p) + o for o, p in parts)
    y = np.zeros(n)
    for o, p in parts:
        y[o:o + len(p)] += p
    return y

def save(name, y, gain=0.5):
    y = y / (np.max(np.abs(y)) + 1e-9) * gain
    fade = int(SR * 0.01)
    y[-fade:] *= np.linspace(1, 0, fade)
    data = (y * 32767).astype('<i2')
    with wave.open(os.path.join(out, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())

ms = lambda v: int(SR * v / 1000)

# move: a soft, round tick, like a fingertip on glass
x = t(0.05)
tick = np.sin(2 * np.pi * 2100 * x) * env(len(x), 0.001, 0.008) + 0.6 * np.sin(2 * np.pi * 900 * x) * env(len(x), 0.001, 0.012)
save('mii_move', tick, 0.5)

# select: a bubbly pop that rises
save('mii_select', mix((0, glide(520, 980, 0.12, 0.045)), (ms(18), 0.35 * bell(1960, 0.18, 0.05))), 0.7)

# back: the same pop, falling
save('mii_back', glide(900, 480, 0.11, 0.04), 0.65)

# launch: a bright rising three-note chime (C6, E6, G6, then C7 shimmer)
notes = [1046.5, 1318.5, 1568.0, 2093.0]
save('mii_launch', mix(*[(ms(70 * i), bell(f, 1.0, 0.32) * (0.9 if i < 3 else 0.5)) for i, f in enumerate(notes)]), 0.75)

# page: an airy swoosh (filtered noise sweeping up and away)
rng = np.random.default_rng(7)
n = int(SR * 0.26)
noise = rng.standard_normal(n)
spec = np.fft.rfft(noise)
freqs = np.fft.rfftfreq(n, 1 / SR)
spec *= np.exp(-((freqs - 2400) / 1600) ** 2)
sw = np.fft.irfft(spec, n)
xs = np.arange(n) / n
sw *= np.sin(np.pi * xs) ** 1.6 * (1 - 0.5 * xs)
save('mii_page', sw, 0.55)

# home: the HOME Menu opening, a gentle two-tone "bwoop" with a bell on top
save('mii_home', mix((0, glide(440, 660, 0.14, 0.07)), (ms(120), glide(660, 990, 0.16, 0.09)), (ms(130), 0.4 * bell(1980, 0.5, 0.15))), 0.7)

# update / notice: two soft bells, like a message arriving
save('mii_notice', mix((0, bell(1318.5, 0.6, 0.2)), (ms(140), bell(1760, 0.8, 0.25))), 0.7)
