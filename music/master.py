"""Run after render.py. Requires FFmpeg, NumPy and SciPy."""
import json, subprocess
from pathlib import Path
import numpy as np
from scipy.signal import butter, sosfilt, hilbert
def ff(*args):
    return subprocess.run(['ffmpeg','-hide_banner','-nostdin','-y',*map(str,args)],check=True,capture_output=True)
def measure(path):
    msg=ff('-i',path,'-af','loudnorm=I=-20:TP=-1:LRA=11:print_format=json','-f','null','-').stderr.decode()
    return json.JSONDecoder().raw_decode(msg[msg.rindex('{'):])[0]
def envelope(path,secs):
    block=ff('-ss',secs,'-i',path,'-t',30,'-f','f32le','-acodec','pcm_f32le','-').stdout
    data=np.frombuffer(block,'<f4').reshape(-1,2)
    env=np.abs(hilbert(sosfilt(butter(4,[300,2000],fs=48000,btype='band',output='sos'),data,axis=0),axis=0))
    spec=np.abs(np.fft.rfft((env-env.mean(axis=0))*np.hanning(len(env))[:,None],axis=0))
    return np.round(20*np.log10(spec[480]/np.median(spec[np.r_[450:477,484:511]],axis=0)),2).tolist()
def main():
    names=['A-control','B-full-mix','C-pad-only']
    sources=[measure(n+'-raw.wav') for n in names]
    target=min([-20]+[float(m['input_i'])-1.1-float(m['input_tp']) for m in sources])
    report={}
    for n,m in zip(names,sources):
        gain=target-float(m['input_i'])
        ff('-loglevel','error','-i',n+'-raw.wav','-af',f'volume={gain:.3f}dB','-c:a','pcm_s24le',n+'.wav')
        report[n]={'static_gain_db':gain}
    ff('-loglevel','error','-i','C-pad-only.wav','-c:a','aac','-b:a','256k','-movflags','+faststart','-metadata','title=Still Current','Still-Current.m4a')
    ff('-loglevel','error','-i','B-full-mix.wav','-c:a','aac','-b:a','256k','-movflags','+faststart','-metadata','title=Still Current - Full Mix','still_current_full.m4a')
    for n in ('A-control','C-pad-only'):
        ff('-loglevel','error','-ss',32,'-i',n+'.wav','-t',120,'-af','afade=t=in:d=1,afade=t=out:st=117:d=3','-c:a','flac',n+'-preview.flac')
    for n,path in [(n,n+'.wav') for n in names]+[('app','Still-Current.m4a')]:
        m=measure(path)
        report.setdefault(n,{}).update(integrated_lufs=m['input_i'],true_peak_dbtp=m['input_tp'],pulse_16hz_db_above_local_median={s:envelope(path,s) for s in (80,650,1250)})
    Path('measurements.json').write_text(json.dumps(report,indent=2))
if __name__=='__main__': main()

