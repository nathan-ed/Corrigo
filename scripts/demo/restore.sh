#!/bin/bash
# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
S=${CORRIGO_DEMO_WORK:-$HOME/.cache/corrigo-demo/work}
REPO=$(cd "$(dirname "$0")/../.." && pwd)
L=$1; D="$S/demo/$L/teacher/Documents/Evaluations/2025-2026/3M/Test 2"
rm -rf "$D"; cp -r $S/demo/test2-$L.pristine "$D"
python3 "$(dirname "$0")/open_eval.py" $L "Test 2" ${2:-0} >/dev/null
