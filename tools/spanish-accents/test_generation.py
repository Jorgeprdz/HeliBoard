# SPDX-License-Identifier: GPL-3.0-only
import unittest
from generate import derive

class SpanishAccentGenerationTest(unittest.TestCase):
    def test_only_unique_frequent_acute_is_evidence(self):
        self.assertEqual({'tambien':'también'},derive({'también':176,'después':63,'Jose':150,'José':150}))
    def test_bare_spanish_form_vetoes_accent_even_when_uncommon(self):
        self.assertEqual({},derive({'termino':1,'término':200,'Tambien':1,'también':176}))
    def test_ambiguity_is_checked_before_frequency_filter(self):
        self.assertEqual({},derive({'terminó':150,'término':1,'publicó':180,'público':160}))
    def test_case_short_words_and_other_diacritics_are_excluded(self):
        self.assertEqual({},derive({'sí':180,'dé':180,'José':180,'mañana':180,'pingüino':180}))

if __name__=='__main__':unittest.main()
