#!/usr/bin/env python3
"""TreinoSmart: leitura de CSV exportado do Android, sem instalar bibliotecas.
Uso: python analytics/analyze_events.py caminho/treinosmart_eventos.csv
Nunca estima repeticoes nem deriva inicio de serie apenas com BPM.
"""
import csv
import json
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from statistics import mean


def load_events(path):
    with open(path, newline='', encoding='utf-8-sig') as f:
        return list(csv.DictReader(f))


def data(row):
    try:
        value = json.loads(row.get('value', ''))
        return value if isinstance(value, dict) else {}
    except (json.JSONDecodeError, TypeError):
        return {}


def summarize(rows):
    sessions = defaultdict(lambda: {'durations_s': [], 'rest_s': [], 'confirmed_volume_kg_reps': 0.0,
                                    'confirmed_sets': 0, 'feedback': [], 'source': 'real'})
    bpm_samples = []
    for row in rows:
        event = row.get('event_type', '')
        if event == 'hr':
            try:
                bpm = int(row['value'])
                stamp = datetime.fromisoformat(row['timestamp_utc'].replace('Z', '+00:00'))
                bpm_samples.append((stamp, bpm))
            except (ValueError, KeyError):
                pass
        if event not in ('set_end', 'rest_end', 'feedback', 'session_start'):
            continue
        value = data(row)
        sid = value.get('session_id')
        if sid is None:
            # O registro de session_start utiliza ID diretamente no CSV.
            if event == 'session_start':
                sid = row.get('value', '')
            else:
                continue
        if not sid:
            continue
        bucket = sessions[str(sid)]
        if row.get('source') == 'simulation' or value.get('demo') is True:
            bucket['source'] = 'simulation'
        if event == 'set_end':
            duration = value.get('duration_ms')
            if isinstance(duration, (int, float)):
                bucket['durations_s'].append(round(duration / 1000, 2))
            reps, load = value.get('actual_reps'), value.get('actual_load_kg')
            if isinstance(reps, int) and isinstance(load, (int, float)):
                bucket['confirmed_volume_kg_reps'] += reps * load
                bucket['confirmed_sets'] += 1
        elif event == 'rest_end':
            duration = value.get('duration_ms')
            if isinstance(duration, (int, float)):
                bucket['rest_s'].append(round(duration / 1000, 2))
        elif event == 'feedback':
            bucket['feedback'].append(value.get('message', ''))
    output = {}
    for sid, bucket in sessions.items():
        durations = bucket.pop('durations_s')
        rests = bucket.pop('rest_s')
        output[sid] = {'source': bucket['source'], 'sets_completed': len(durations),
            'set_durations_s': durations, 'average_set_s': round(mean(durations), 2) if durations else None,
            'actual_rest_s': rests, 'average_rest_s': round(mean(rests), 2) if rests else None,
            'confirmed_volume_kg_reps': round(bucket['confirmed_volume_kg_reps'], 2),
            'sets_with_confirmed_volume': bucket['confirmed_sets'], 'feedback': bucket['feedback']}
    return {'sessions': output, 'hr_samples': len(bpm_samples),
            'hr_range_bpm': [min(h for _, h in bpm_samples), max(h for _, h in bpm_samples)] if bpm_samples else None,
            'note': 'HR e apenas telemetria; nao confirma repeticoes, tecnica nem descanso muscular.'}


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Uso: python analytics/analyze_events.py treinosmart_eventos.csv')
    print(json.dumps(summarize(load_events(Path(sys.argv[1]))), ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
