// SPDX-License-Identifier: GPL-3.0-only
package com.android.inputmethod.latin.makedict;

import java.io.File;
import java.io.RandomAccessFile;

/** Uses the upstream Java trie reader; avoids its optional JNI header/iteration wrappers. */
public final class DecodeEmojiDictionary {
    private static boolean printWords = true;
    private static int words;
    private static void walk(Ver2DictDecoder decoder, int address, String prefix) {
        decoder.setPosition(address);
        int count = decoder.readPtNodeCount();
        for (int i = 0; i < count; i++) {
            PtNodeInfo node = decoder.readPtNode(decoder.getPosition());
            String word = prefix + new String(node.mCharacters, 0, node.mCharacters.length);
            if (node.isTerminal()) {
                words++;
                if ((node.mFlags & 2) == 0) throw new IllegalArgumentException("Keyword marked as a real word: " + word);
                if (printWords) System.out.println(" word=" + word + ",f=" + node.mProbabilityInfo.mProbability);
                if (node.mShortcutTargets != null) {
                    for (WeightedString shortcut : node.mShortcutTargets) {
                        if (shortcut.mProbabilityInfo.mProbability >= 15) throw new IllegalArgumentException("Auto-correction whitelist shortcut");
                        if (printWords) System.out.println("  shortcut=" + shortcut.mWord + ",f=" + shortcut.mProbabilityInfo.mProbability);
                    }
                }
            }
            if (node.mChildrenAddress > 0) walk(decoder, node.mChildrenAddress, word);
            decoder.setPosition(node.mEndAddress);
        }
    }
    public static void main(String[] args) throws Exception {
        printWords = !args[0].equals("--check");
        for (int index = printWords ? 0 : 1; index < args.length; index++) {
        words = 0;
        File file = new File(args[index]);
        int body;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.readInt() != 0x9BC13AFE) throw new IllegalArgumentException("Invalid magic");
            int version = input.readUnsignedShort();
            if (version != 2 && version != 201 && version != 202) throw new IllegalArgumentException("Unsupported format " + version);
            if (input.readUnsignedShort() != 0) throw new IllegalArgumentException("Expected no code point table");
            body = input.readInt();
        }
        Ver2DictDecoder decoder = new Ver2DictDecoder(file, 0, file.length(), 0);
        decoder.openDictBuffer();
        walk(decoder, body, "");
        if (words == 0) throw new IllegalArgumentException("Empty dictionary: " + file);
        if (!printWords) System.out.println(file.getName() + ": " + words + " keywords");
        }
    }
}
