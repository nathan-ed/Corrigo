# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Lists the copies of an evaluation in the app (stopped) and opens the given one."""
import sys, yaml, glob, os
S = os.environ.get('CORRIGO_DEMO_WORK', os.path.expanduser('~/.cache/corrigo-demo/work'))
lang, name, first = sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0
folder = f'{S}/demo/{lang}/teacher/Documents/Evaluations/2025-2026/3M/{name}'
files = sorted(glob.glob(folder + '/*.pdf'))
p = f'{S}/demo/xdg-{lang}/Corrigo/userdata.yml'
d = (yaml.safe_load(open(p)) if os.path.exists(p) else None) or {}
d.setdefault('files', {})['lastFiles'] = files
d['files']['lastFile'] = files[first]
yaml.safe_dump(d, open(p, 'w'), allow_unicode=True)
print(files[first])
