# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Empty app state for a take: no copies listed, the demo scan alone in its class folder."""
import sys, os, shutil, yaml, glob
S = os.environ.get('CORRIGO_DEMO_WORK', os.path.expanduser('~/.cache/corrigo-demo/work'))
lang = sys.argv[1]
home = f'{S}/demo/{lang}/teacher'
klass = f'{home}/Documents/Evaluations/2025-2026/2M'
shutil.rmtree(klass + '/Test 3', ignore_errors=True)
data = f'{S}/demo/xdg-{lang}/Corrigo'
p = data + '/userdata.yml'
d = (yaml.safe_load(open(p)) if os.path.exists(p) else None) or {}
d['lastOpenDir'] = klass
d.setdefault('files', {})['lastFiles'] = []
d['files']['lastFile'] = ''
yaml.safe_dump(d, open(p, 'w'), allow_unicode=True)
for f in ['evaluations.yml', 'exercisecorrection.yml']:
    if os.path.exists(f'{data}/{f}'): os.remove(f'{data}/{f}')
shutil.rmtree(data + '/editions', ignore_errors=True)
print('reset', lang)
