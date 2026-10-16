# SPDX-License-Identifier: GPL-3.0-only
"""Reporting only: distinguish generator/ranker truncation from model selection."""
def candidate_diagnosis(hits,shown,baseline_tp,selected_tp):
 generated=any(hits);visible=any(hits[:shown])
 return dict(generatedUseful=generated,shownUseful=visible,
  withheldByPromptBudget=generated and not visible,
  shownUsefulNotSelected=visible and selected_tp<=baseline_tp)
