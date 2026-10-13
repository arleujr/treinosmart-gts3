"""API demonstrativa sem banco remoto: recebe eventos e calcula resumo.
Por padrao iniciar SOMENTE em localhost. Nao exponha dados de saude na Internet.
"""
from fastapi import FastAPI
from pydantic import BaseModel, Field
from analytics.analyze_events import summarize

app = FastAPI(title='TreinoSmart Analysis API', version='0.3.0')

class Event(BaseModel):
    timestamp_utc: str = ''
    event_type: str
    source: str = ''
    value: str = ''
    raw_hex: str = ''
    note: str = ''

class Payload(BaseModel):
    events: list[Event] = Field(max_length=100000)

@app.get('/health')
def health():
    return {'ok': True, 'service': 'treinosmart-analytics', 'version': '0.3.0'}

@app.post('/sessions/analyze')
def analyze(payload: Payload):
    return summarize([e.model_dump() for e in payload.events])
