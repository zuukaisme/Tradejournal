import unittest
from datetime import timezone
from unittest.mock import patch

import mt5_connector


class ConnectorTests(unittest.TestCase):
    def test_parse_utc_normalizes_offset(self):
        value = mt5_connector.parse_utc("2025-01-01T03:00:00+03:00")
        self.assertEqual(value.tzinfo, timezone.utc)
        self.assertEqual(value.hour, 0)

    def test_parse_utc_requires_timezone(self):
        with self.assertRaises(ValueError):
            mt5_connector.parse_utc("2025-01-01T00:00:00")

    def test_rates_csv_has_standard_ohlcv_header(self):
        class Rate(dict):
            pass

        rows = [Rate(time=1735689600, open=1.1, high=1.2, low=1.0, close=1.15, tick_volume=10, spread=2, real_volume=0)]
        csv_text = mt5_connector.rates_to_csv(rows)
        self.assertTrue(csv_text.startswith("time,open,high,low,close,tick_volume,spread,real_volume\n"))
        self.assertIn("2025-01-01 00:00:00", csv_text)


if __name__ == "__main__":
    unittest.main()
