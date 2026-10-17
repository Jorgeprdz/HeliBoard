# SPDX-License-Identifier: GPL-3.0-only
import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("synthetic",Path(__file__).parents[1]/"synthetic_corpus.py")
synthetic=importlib.util.module_from_spec(spec);spec.loader.exec_module(synthetic)
class SyntheticTests(unittest.TestCase):
 def test_all_noise_channels_are_reproducible_and_changed(self):
  source="we can receive the documents tomorrow morning"
  for kind in synthetic.KINDS:
   first,events=synthetic.inject(source,kind,91)
   self.assertNotEqual(first,source,kind);self.assertEqual(first,synthetic.inject(source,kind,91)[0],kind)
   self.assertEqual(events[0]["before"],source);self.assertEqual(events[-1]["after"],first)
 def test_noise_does_not_modify_numbers_or_capitalized_names(self):
  text="No Ana quiere recibir 123 documentos"
  for kind in ("neighbor","duplicate","omit","transpose","multiple"):
   result,_=synthetic.inject(text,kind,92);self.assertIn("No Ana",result);self.assertIn("123",result)
 def test_windows_are_original_disjoint_substrings(self):
  source=" ".join(["independent source words provide enough content"]*20)
  windows=synthetic.source_windows(source)
  self.assertGreater(len(windows),1)
  for a,b in zip(windows,windows[1:]):self.assertLessEqual(a["end"],b["start"])
  for row in windows:self.assertEqual(row["original"],source[row["start"]:row["end"]])

class DiagnosticTests(unittest.TestCase):
 def test_useful_candidate_outside_prompt_is_not_model_selection_failure(self):
  spec=importlib.util.spec_from_file_location("diagnostics",Path(__file__).parents[1]/"synthetic_diagnostics.py")
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  result=module.candidate_diagnosis([0,0,1],2,0,0)
  self.assertTrue(result["generatedUseful"]);self.assertFalse(result["shownUseful"])
  self.assertTrue(result["withheldByPromptBudget"]);self.assertFalse(result["shownUsefulNotSelected"])
 def test_shown_candidate_can_identify_selection_failure(self):
  spec=importlib.util.spec_from_file_location("diagnostics",Path(__file__).parents[1]/"synthetic_diagnostics.py")
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  self.assertTrue(module.candidate_diagnosis([1,0],2,0,0)["shownUsefulNotSelected"])
 def test_successful_selection_is_not_counted_as_failure(self):
  spec=importlib.util.spec_from_file_location("diagnostics",Path(__file__).parents[1]/"synthetic_diagnostics.py")
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  self.assertFalse(module.candidate_diagnosis([1],1,0,1)["shownUsefulNotSelected"])

if __name__=="__main__":unittest.main()
