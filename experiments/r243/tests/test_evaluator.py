# SPDX-License-Identifier: GPL-3.0-only
import argparse,hashlib,importlib.util,json,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("evaluation",Path(__file__).parents[1]/"evaluate.py")
evaluation=importlib.util.module_from_spec(spec);spec.loader.exec_module(evaluation)
class EvaluationTests(unittest.TestCase):
 def test_java_offsets_after_emoji_map_to_python_without_text_loss(self):
  self.assertEqual(evaluation.codepoint_offset("😀 recbir",3),2)
 def test_incorrect_change_is_not_an_improvement(self):
  result=evaluation.metrics("cat","car","cap")
  self.assertEqual((result["tp"],result["fp"],result["fn"],result["residual"]),(0,1,1,2))
 def test_aggregate_keeps_introduced_errors_in_residual(self):
  self.assertEqual(evaluation.aggregate([evaluation.metrics("cat","car","cap")])["residual"],2)
 def test_correct_edit_scores_precision_recall_and_f05(self):
  result=evaluation.aggregate([evaluation.metrics("cat","car","car")])
  self.assertEqual((result["precision"],result["recall"],result["f05"],result["residual"]),(1,1,1,0))
 def test_abstention_is_reported_separately_from_correctness(self):
  result=evaluation.aggregate([evaluation.metrics("cat","car","cat")])
  self.assertIsNone(result["precision"]);self.assertEqual(result["recall"],0);self.assertEqual(result["abstentions"],1)
 def test_clean_text_does_not_invent_recall(self):
  result=evaluation.aggregate([evaluation.metrics("hola","hola","hola")])
  self.assertIsNone(result["recall"]);self.assertIsNone(result["precision"]);self.assertEqual(result["incorrectChanges"],0)
 def test_missing_corpus_blocks_before_runtime_or_download(self):
  result=evaluation.run(argparse.Namespace(corpus=None))
  self.assertEqual(result["gate"],"BLOCKED");self.assertIsNone(result["comparativeResults"])
 def comparison_fixture(self):
  # Hand-built metric totals exercise gate decisions, not a fabricated language benchmark.
  return {system:{group:{mode:{"residual":10 if system!="r243-prototype" else 8,"incorrectChanges":0} for mode in ["automatic","assisted"]} for group in ["es-MX","en-US","mixed","protected"]} for system in evaluation.SYSTEMS}
 def test_mixed_language_regression_cannot_pass(self):
  comparison=self.comparison_fixture();comparison["r243-prototype"]["mixed"]["assisted"]["residual"]=11
  self.assertEqual(evaluation.gate(comparison,True),"FAIL")
 def test_candidate_must_not_regress_against_r242(self):
  comparison=self.comparison_fixture();comparison["r242-component"]["es-MX"]["assisted"]["residual"]=7
  self.assertEqual(evaluation.gate(comparison,True),"FAIL")
 def test_protected_change_blocks_otherwise_improved_candidate(self):
  comparison=self.comparison_fixture();comparison["r243-prototype"]["protected"]["assisted"]["incorrectChanges"]=1
  self.assertEqual(evaluation.gate(comparison,True),"FAIL")
 def test_component_totals_meeting_all_conditions_can_pass(self):
  self.assertEqual(evaluation.gate(self.comparison_fixture(),True),"PASS")
 def test_no_human_review_cannot_pass(self):
  self.assertEqual(evaluation.gate({},False),"BLOCKED")
 def test_reference_derived_vocabulary_rejected(self):
  with tempfile.TemporaryDirectory() as tmp:
   lexicon=Path(tmp)/"vocab.tsv";lexicon.write_text("hola\t1\n")
   data={"provenance":{"independent_from_development":True,"human_annotated":True,"publishable":True,"human_reviewers":["a","b"],"source":"component-test-only","license":"fixture","lexicon_source":"fixture","lexicon_sha256":evaluation.digest(lexicon),"lexicon_from_references":True},"messages":[]}
   with self.assertRaisesRegex(ValueError,"Independent fixed vocabulary"):evaluation.validate_corpus(data,lexicon)
 def test_synthetic_or_unreviewed_corpus_cannot_be_blind(self):
  with tempfile.TemporaryDirectory() as tmp:
   lexicon=Path(tmp)/"vocab.tsv";lexicon.write_text("")
   with self.assertRaisesRegex(ValueError,"Independent human"):evaluation.validate_corpus({"provenance":{}},lexicon)
 def test_prompt_contains_options_but_never_gold_reference(self):
  prompt=evaluation.selection_prompt("recbir",{"0":"recbir","1":"recibir"})
  self.assertIn("recibir",prompt);self.assertNotIn("reference",prompt)
 def test_changed_output_without_correct_edit_is_counted_as_false(self):
  result=evaluation.metrics("No quiero 3","No quiero 3","Quiero 3")
  self.assertGreater(result["fp"],0);self.assertTrue(result["changed"])
if __name__=="__main__":unittest.main()
