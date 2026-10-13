import unittest
from analytics.analyze_events import summarize

class AnalyticsTests(unittest.TestCase):
    def test_no_fake_volume_or_reps(self):
        rows = [
            {'event_type':'session_start','value':'s1','source':'user'},
            {'event_type':'set_end','value':'{"session_id":"s1","duration_ms":30000,"actual_reps":null,"actual_load_kg":null}','source':'app'},
            {'event_type':'rest_end','value':'{"session_id":"s1","duration_ms":92000}','source':'user'},
            {'event_type':'set_end','value':'{"session_id":"s1","duration_ms":50000,"actual_reps":12,"actual_load_kg":20}','source':'app'},
        ]
        summary = summarize(rows)['sessions']['s1']
        self.assertEqual(summary['set_durations_s'], [30,50])
        self.assertEqual(summary['actual_rest_s'], [92])
        self.assertEqual(summary['confirmed_volume_kg_reps'], 240)
        self.assertEqual(summary['sets_with_confirmed_volume'], 1)

if __name__ == '__main__': unittest.main()
