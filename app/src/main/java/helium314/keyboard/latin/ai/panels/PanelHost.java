// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.panels;
/** All methods run on the UI thread. Mount via LatinIME.showInternalInputPanel(view). */
public interface PanelHost {
 /** Stable equality token covering editor identity, selection and revision. Never return null. */
 Object editorToken();
 /** Must include sensitive-field policy and current network consent. */
 boolean canUseNetwork();
 /** Revalidate original editor snapshot, commit only this accepted text, and return success. */
 boolean commitText(String acceptedText);
 /** Close the overlay and restore ABC. Host must also call panel.close() on editor changes/hide. */
 void closePanel();
}
