import unittest
from analysis.forecast_v1.backtest import estimate,historical_at,quantile
class ReplayTest(unittest.TestCase):
    def test_future_batches_do_not_change_forecast(self):
        past=[(i,20.) for i in range(10)]
        before=estimate(61,historical_at(past,20),None,45)
        after=estimate(61,historical_at(past+[(100+i,200.) for i in range(100)],20),None,45)
        self.assertEqual(before,after)
        self.assertEqual(before['eta'],180.)
    def test_stationary_time_not_removed_from_history(self):
        self.assertIsNone(historical_at([(i,0 if i<6 else 100) for i in range(10)],20))
    def test_no_speed_has_no_eta(self):self.assertIsNone(estimate(50,None,None))
    def test_single_batch_cannot_dominate_history(self):
        self.assertEqual(estimate(101,(20,100),(80,1),50)['speed'],29.)
    def test_live_only_is_low_confidence(self):
        result=estimate(31,None,(20,10))
        self.assertEqual((result['eta'],result['low'],result['high'],result['confidence']),(90.,45.,135.,'LOW'))
    def test_front_is_not_negative_time(self):self.assertEqual(estimate(1,None,None)['eta'],0.)
    def test_quantiles(self):self.assertEqual(quantile([0,10,20],.8),16.)
if __name__=='__main__':unittest.main()
