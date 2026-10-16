"""Publish and serve one pinned APK. No upload or filesystem browsing API."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import threading

MAX_APK=128*1024*1024
RELEASES=Path(__file__).resolve().parent.parent/'artifacts'/'releases'

class UpdateStore:
    def __init__(self,root=RELEASES):self.root=Path(root);self.lock=threading.Lock();self.cached=None
    def load(self):
        with self.lock:
            manifest=self.root/'latest.json'
            data=json.loads(manifest.read_text(encoding='utf-8'))
            name=data.get('file','')
            if not re.fullmatch(r'[A-Za-z0-9._-]+\.apk',name):raise ValueError('invalid_release')
            if data.get('package')!='dev.grokhome' or type(data.get('version_code')) is not int or data['version_code']<=0:raise ValueError('invalid_release')
            if not isinstance(data.get('version_name'),str) or len(data['version_name'])>100:raise ValueError('invalid_release')
            digest=data.get('sha256','')
            if not re.fullmatch(r'[a-f0-9]{64}',digest):raise ValueError('invalid_release')
            path=self.root/name
            if path.is_symlink() or path.resolve().parent!=self.root.resolve():raise ValueError('invalid_release')
            stat=path.stat()
            if not 0<stat.st_size<=MAX_APK or data.get('size')!=stat.st_size:raise ValueError('invalid_release')
            key=(digest,stat.st_mtime_ns,stat.st_size)
            if self.cached and self.cached[0]==key:body=self.cached[1]
            else:
                body=path.read_bytes()
                if hashlib.sha256(body).hexdigest()!=digest:raise ValueError('invalid_release')
                self.cached=(key,body)
            public={key:data[key] for key in ('package','version_code','version_name','size','sha256')}
            public['apk_path']='/app/update.apk?sha256='+digest
            return public,body

def publish(apk,version_code,version_name,root=RELEASES):
    root=Path(root);root.mkdir(parents=True,exist_ok=True)
    body=Path(apk).read_bytes()
    if not 0<len(body)<=MAX_APK or version_code<=0:raise ValueError('invalid_apk')
    digest=hashlib.sha256(body).hexdigest();name=f'GrokHome-{version_code}-{digest[:12]}.apk'
    target=root/name
    if not target.exists():
        temporary=root/(name+'.tmp');temporary.write_bytes(body);os.replace(temporary,target)
    data={'package':'dev.grokhome','version_code':version_code,'version_name':version_name,'file':name,'size':len(body),'sha256':digest}
    temporary=root/'latest.json.tmp';temporary.write_text(json.dumps(data,indent=2),encoding='utf-8');os.replace(temporary,root/'latest.json')
    UpdateStore(root).load()
    return data

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('apk');parser.add_argument('--version-code',type=int,required=True);parser.add_argument('--version-name',required=True);parser.add_argument('--output',type=Path,default=RELEASES)
    args=parser.parse_args();print(json.dumps(publish(args.apk,args.version_code,args.version_name,args.output)))
