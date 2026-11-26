"""Still Current, original score and deterministic stereo synthesizer. Python 3, NumPy and SciPy.
Run from an output directory with: python render.py. Then run master.py.
"""
import wave
from pathlib import Path
from contextlib import ExitStack
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

FS = 48000
BAR = 2*FS
TAU = 2*np.pi
rng = np.random.default_rng(17035)
local = np.arange(BAR)/FS
voicings = [[50,57,60,64,65],[47,55,57,62,64],[48,55,59,62,64],[50,53,57,60,64]]
roots = [38,31,36,38]
def hz(note):
    return 440*2**((note-69)/12)
def smooth(x):
    x=np.clip(x,0,1)
    return x*x*(3-2*x)
def cloud(note,t,channel):
    f=hz(note)
    angle=TAU*f*t
    drift=.20*np.sin(t*.37+note)+.16*np.sin(t*.58+channel*.5+note)
    wave1=np.sin(angle+drift)+.29*np.sin(2*angle+drift)+.13*np.sin(3*angle+.3)
    detune=(1.0013 if channel else .9988)
    wave2=np.sin(angle*detune+.7+drift)+.19*np.sin(2*angle*detune)
    return (.72*wave1+.28*wave2)*(.88+.12*np.sin(.11*t+note))
# A broken chord played by softly struck tines, with sympathetic overtones.
def piano(note,vel,length):
    t=np.arange(int((length+2.4)*FS))/FS
    w=TAU*hz(note)*t
    sustain=np.exp(-t/(1.5+vel)) * np.exp(-np.maximum(0,t-length)*3.1)
    attack=1-np.exp(-t/.009)
    strike=(.45+vel)*np.exp(-t/.18)
    return .17*vel*attack*sustain*(np.sin(w+strike*np.sin(2.001*w))+
           .22*np.sin(2.003*w+.13*np.sin(w))*np.exp(-t/.9)+
           .08*np.sin(3.99*w)*np.exp(-t/.38))
def pan(signal,p):
    return signal[:,None]*np.array([np.sqrt(1-p),np.sqrt(p)])
def insert(bus,secs,signal):
    i=round(secs*FS)
    n=min(len(signal),len(bus)-i)
    if n>0: bus[i:i+n]+=signal[:n]
def room_kernel():
    t=np.arange(int(1.65*FS))/FS
    noise=sosfilt(butter(2,3200,fs=FS,output='sos'),rng.normal(size=len(t)))
    noise*=np.exp(-5*t)*(1-np.exp(-t*32))
    noise/=np.sqrt(np.sum(noise**2))
    for dt,v in [(.029,.25),(.047,.19),(.071,.14),(.107,.10)]:
        noise[int(FS*dt)]+=v
    return noise
kernels=[room_kernel(),room_kernel()]
melodies=[[(0,69),(.79,72)],[(.26,76),(1.28,74)],[(.52,69)],[(1.01,67)],
          [(.04,69),(1.29,72)],[(.48,76),(1.51,74)],[(.76,67)],[]]
def write24(w,s):
    if np.max(np.abs(s))>=1: raise RuntimeError("Clipped source")
    i=np.rint(s*8388607).astype('<i4')
    w.writeframesraw(i.view(np.uint8).reshape(-1,4)[:,:3].copy().tobytes())
def run():
    with ExitStack() as stack:
        files=[]
        for name in ('A-control','B-full-mix','C-pad-only'):
            file=stack.enter_context(wave.open(name+'-raw.wav','wb'))
            file.setparams((2,3,FS,0,'NONE','PCM'))
            files.append(file)
        tails=np.zeros((5*BAR,2))
        room_tail=np.zeros((len(kernels[0])-1,2))
        for bar in range(750):
            t=local+2*bar
            chord=(bar//4)%4 if bar<744 else 3
            prev=((bar-1)//4)%4 if 0<bar<=744 else chord
            blend=smooth(local/1.4) if bar%4==0 else np.ones(BAR)
            pad=np.zeros((BAR,2))
            for n in set(voicings[chord]+voicings[prev]):
                gain=(blend if n in voicings[chord] else 0)+(1-blend if n in voicings[prev] else 0)
                for ch in range(2):
                    pad[:,ch]+=.041*gain*cloud(n,t,ch)*(1+(.11 if ch else -.11)*np.sin(n))
            # Long arcs grow and recede across the form; keep the motif recognizable.
            arc=.84+.12*np.sin(TAU*bar/96)+.04*np.sin(TAU*bar/59)
            pad*=arc
            phrase=bar%8
            notes=list(melodies[phrase])
            if bar//16%3==1 and phrase==3: notes=[(.48,71),(1.45,69)]
            if bar//16%4==2 and phrase==6: notes=[(.52,65),(1.53,64)]
            if bar<4 or bar>=744: notes=[(.46, [69,64,62,62][bar%4])] if bar%2==0 else []
            for off,n in notes:
                vel=rng.uniform(.55,.90)*arc
                insert(tails,off+rng.uniform(.006,.024),pan(piano(n,vel,rng.uniform(.68,1.58)),.36+.17*np.sin(n)))
            if 240<=bar<496 and bar%4 in (2,3):
                n=(79,77,76,74)[(bar//4)%4]
                insert(tails,1.05,pan(piano(n,.28,1.55),.67))
            if 16<=bar<743:
                b=roots[chord]
                length=1.0 if bar%4==3 else 1.75
                bt=local
                w=TAU*hz(b)*bt
                bass=.14*(1-np.exp(-bt/.035))*np.exp(-bt/2.2)*np.exp(-np.maximum(0,bt-length)*6)*(np.sin(w)+.23*np.sin(2*w)+.08*np.sin(3*w))
                tails[:BAR]+=pan(bass,.50)
                if bar%8==7:
                    insert(tails,1.5,pan(piano(b+7,.32,.35),.48))
            if 24<=bar<720 and not (496<=bar<520):
                kt=local[:int(FS*.39)]
                kick=.071*np.sin(TAU*(54*kt+1.7*(1-np.exp(-kt*24))))*np.exp(-kt*14)*smooth(kt/.008)
                insert(tails,.007,pan(kick,.5))
                for off,g in [(1.017,.029),(.756,.010),(1.765,.009)]:
                    nt=local[:int(.22*FS)]
                    brushed=sosfilt(butter(2,[950,6400],btype='band',fs=FS,output='sos'),rng.normal(size=len(nt)))
                    insert(tails,off+rng.uniform(0,.012),pan(g*brushed*smooth(nt/.019)*np.exp(-nt*24),rng.uniform(.39,.64)))
            rest=tails[:BAR].copy()
            tails[:-BAR]=tails[BAR:]
            tails[-BAR:]=0
            for ch in range(2):
                diffusion=fftconvolve(rest[:,ch]*.75+rest[:,1-ch]*.25,kernels[ch])
                diffusion[:len(room_tail)]+=room_tail[:,ch]
                room_tail[:,ch]=diffusion[BAR:]
                rest[:,ch]+=.16*diffusion[:BAR]
            fade=(smooth(t/3)*smooth((1500-t)/9))[:,None]
            g=(.825+.175*np.cos(TAU*16*local))[:,None]
            for file,arr in zip(files,(pad+rest,(pad+rest)*g,pad*g+rest)):
                write24(file,arr*fade)
            if bar%100==0: print(bar,'/ 750',flush=True)
if __name__=='__main__': run()
