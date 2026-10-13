"""Resumir CSV exportado pelo TreinoSmart, sem instalar bibliotecas externas.

Uso: python analytics/resumo.py /caminho/treinosmart_eventos.csv
"""
import csv
import sys
from pathlib import Path
from datetime import datetime
from statistics import mean, median


def main(path: Path) -> None:
    with path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        print("CSV vazio")
        return

    print(f"Eventos totais: {len(rows)}")
    print(f"Amostras cardiacas: {sum(r['event_type'] == 'hr' for r in rows)}")
    print(f"Pacotes Moyoung: {sum(r['event_type'] in ('moyoung', 'moyoung_aux') for r in rows)}")
    gaps = [r for r in rows if r['event_type'] == 'hr_gap']
    silences = [r for r in rows if r['event_type'] == 'hr_silence']
    disconnects = [r for r in rows if r['event_type'] == 'connection']
    print(f"Lacunas detectadas (retorno do sinal): {len(gaps)}")
    print(f"Avisos de silencio de HR (>5s): {len(silences)}")
    print(f"Interrupcoes de conexao: {len(disconnects)}")
    hr_rows = [r for r in rows if r["event_type"] == "hr" and r["value"].isdigit()]
    if hr_rows:
        bpm = [int(r["value"]) for r in hr_rows]
        print(f"BPM observado (nao validado clinicamente): media {mean(bpm):.1f}, minimo {min(bpm)}, maximo {max(bpm)}")
        instants = [datetime.fromisoformat(r['timestamp_utc']) for r in hr_rows]
        intervals = [(b-a).total_seconds() for a,b in zip(instants, instants[1:]) if (b-a).total_seconds() >= 0]
        if intervals:
            print(f"Intervalo entre amostras: mediana {median(intervals):.2f}s; maior {max(intervals):.2f}s")

    starts = []
    for row in rows:
        if row["event_type"] == "set_start":
            starts.append(row)
        elif row["event_type"] == "set_end":
            if not starts:
                print("Serie finalizada sem inicio registrado")
                continue
            started = starts.pop(0)
            t0 = datetime.fromisoformat(started["timestamp_utc"])
            t1 = datetime.fromisoformat(row["timestamp_utc"])
            during = [int(r["value"]) for r in hr_rows
                      if t0 <= datetime.fromisoformat(r["timestamp_utc"]) <= t1]
            duration = (t1 - t0).total_seconds()
            suffix = f" | {len(during)} leituras de HR; BPM medio={mean(during):.1f}" if during else " | Sem BPM registrado (nao inferir descanso)"
            print(f"Serie {started['value']}: {duration:.1f} s{suffix}")
    if starts:
        print(f"{len(starts)} serie(s) ainda sem encerramento")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Uso: python analytics/resumo.py treinosmart_eventos.csv")
    main(Path(sys.argv[1]))
