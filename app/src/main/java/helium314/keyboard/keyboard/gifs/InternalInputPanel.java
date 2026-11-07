// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gifs;

/** Input belonging to a panel must never reach the application draft. */
public interface InternalInputPanel {
    void onTextInput(String text);
    void onCodeInput(int codePoint);
    void close();
}
